from __future__ import annotations

import argparse
import asyncio
import json
import subprocess
import threading
import time
from pathlib import Path
from typing import Any

import websockets
from pynput import keyboard, mouse
from websockets.asyncio.server import ServerConnection, serve


class UbuntuAndroidBridge:
    """Share one physical mouse/keyboard between Ubuntu and Android with a toggle hotkey."""

    def __init__(self, config_path: str) -> None:
        self.config = json.loads(Path(config_path).read_text(encoding="utf-8"))
        self.host = str(self.config.get("host", "0.0.0.0"))
        self.port = int(self.config.get("port", 24891))
        self.name = str(self.config.get("name", "NEAL-UBUNTU"))

        self.tablet: ServerConnection | None = None
        self.loop: asyncio.AbstractEventLoop | None = None
        self.stop_event = threading.Event()
        self.state_lock = threading.RLock()

        self.width, self.height = self._screen_size()
        self.center_x = self.width // 2
        self.center_y = self.height // 2
        self.last_host_x = self.center_x
        self.last_host_y = self.center_y
        self.tablet_width = 1080
        self.tablet_height = 2400
        self.tablet_x = self.tablet_width // 2
        self.tablet_y = self.tablet_height // 2
        self.tablet_mode = False
        self.pressed_keys: set[object] = set()
        self.toggle_latched = False
        self.suppress_toggle_keys = False
        self.ignore_mouse_until = 0.0

    @staticmethod
    def _screen_size() -> tuple[int, int]:
        try:
            out = subprocess.check_output(["xrandr", "--current"], text=True, stderr=subprocess.DEVNULL)
            for line in out.splitlines():
                if " connected " not in line:
                    continue
                for token in line.split():
                    if "x" in token and "+" in token:
                        size = token.split("+")[0]
                        w, h = size.split("x", 1)
                        if w.isdigit() and h.isdigit():
                            return int(w), int(h)
        except Exception:
            pass
        return 1920, 1080

    def _warp_mouse(self, x: int, y: int) -> None:
        self.ignore_mouse_until = time.monotonic() + 0.15
        try:
            subprocess.run(
                ["xdotool", "mousemove", "--sync", str(int(x)), str(int(y))],
                check=False,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except FileNotFoundError:
            print("ERROR: xdotool is required. Install with: sudo apt install xdotool")

    def _send_now(self, payload: dict[str, Any]) -> None:
        with self.state_lock:
            websocket = self.tablet
            loop = self.loop
        if not websocket or not loop:
            return
        raw = json.dumps(payload, separators=(",", ":"))
        asyncio.run_coroutine_threadsafe(websocket.send(raw), loop)

    def send(self, payload: dict[str, Any]) -> None:
        self._send_now(payload)

    def toggle_tablet_mode(self) -> None:
        with self.state_lock:
            if self.tablet is None:
                print("Tablet mode unavailable: no Android tablet connected")
                return
            entering = not self.tablet_mode
            self.tablet_mode = entering

            if entering:
                try:
                    raw = subprocess.check_output(["xdotool", "getmouselocation"], text=True)
                    for token in raw.strip().split():
                        if token.startswith("x:"):
                            self.last_host_x = int(token[2:])
                        elif token.startswith("y:"):
                            self.last_host_y = int(token[2:])
                except Exception:
                    pass
                self.tablet_x = self.tablet_width // 2
                self.tablet_y = self.tablet_height // 2
            else:
                restore_x = max(0, min(self.width - 1, self.last_host_x))
                restore_y = max(0, min(self.height - 1, self.last_host_y))

        if entering:
            self.send({
                "v": 1,
                "type": "mode",
                "mode": "tablet",
                "x": self.tablet_x,
                "y": self.tablet_y,
                "width": self.tablet_width,
                "height": self.tablet_height,
            })
            self._warp_mouse(self.center_x, self.center_y)
            print("Mouse/keyboard ownership -> ANDROID TABLET")
        else:
            self.send({"v": 1, "type": "mode", "mode": "host"})
            self._warp_mouse(restore_x, restore_y)
            print("Mouse/keyboard ownership -> UBUNTU")

    def on_move(self, x: int, y: int) -> None:
        if time.monotonic() < self.ignore_mouse_until:
            return

        with self.state_lock:
            tablet_mode = self.tablet_mode
            connected = self.tablet is not None

        if not connected:
            return

        if not tablet_mode:
            self.last_host_x = x
            self.last_host_y = y
            return

        dx = x - self.center_x
        dy = y - self.center_y
        if dx == 0 and dy == 0:
            return

        with self.state_lock:
            self.tablet_x = max(0, min(self.tablet_width - 1, self.tablet_x + dx))
            self.tablet_y = max(0, min(self.tablet_height - 1, self.tablet_y + dy))
            tx, ty = self.tablet_x, self.tablet_y

        self.send({"v": 1, "type": "move", "dx": int(dx), "dy": int(dy), "x": int(tx), "y": int(ty)})
        self._warp_mouse(self.center_x, self.center_y)

    def on_click(self, _x: int, _y: int, button: mouse.Button, pressed: bool) -> None:
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active:
            self.send({
                "v": 1,
                "type": "click",
                "button": getattr(button, "name", str(button)),
                "pressed": bool(pressed),
            })

    def on_scroll(self, _x: int, _y: int, dx: int, dy: int) -> None:
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active:
            self.send({"v": 1, "type": "scroll", "dx": int(dx), "dy": int(dy)})

    @staticmethod
    def _key_name(key: keyboard.Key | keyboard.KeyCode) -> str:
        if isinstance(key, keyboard.KeyCode):
            if key.char is not None:
                return f"char:{key.char}"
            if key.vk is not None:
                return f"vk:{key.vk}"
        return f"key:{getattr(key, 'name', repr(key))}"

    @staticmethod
    def _is_ctrl(key: object) -> bool:
        return key in {keyboard.Key.ctrl, keyboard.Key.ctrl_l, keyboard.Key.ctrl_r}

    @staticmethod
    def _is_alt(key: object) -> bool:
        return key in {keyboard.Key.alt, keyboard.Key.alt_l, keyboard.Key.alt_r}

    @staticmethod
    def _is_space(key: object) -> bool:
        return key == keyboard.Key.space

    def _check_toggle_combo(self, key: object, pressed: bool) -> bool:
        if pressed:
            self.pressed_keys.add(key)
        else:
            self.pressed_keys.discard(key)

        ctrl = any(self._is_ctrl(k) for k in self.pressed_keys)
        alt = any(self._is_alt(k) for k in self.pressed_keys)
        space = any(self._is_space(k) for k in self.pressed_keys)
        combo_active = ctrl and alt and space

        if combo_active and not self.toggle_latched:
            self.toggle_latched = True
            self.suppress_toggle_keys = True
            self.toggle_tablet_mode()
            return True

        if not combo_active:
            self.toggle_latched = False

        return self.suppress_toggle_keys

    def on_press(self, key: keyboard.Key | keyboard.KeyCode) -> None:
        if self._check_toggle_combo(key, True):
            return
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active:
            self.send({"v": 1, "type": "key", "key": self._key_name(key), "pressed": True})

    def on_release(self, key: keyboard.Key | keyboard.KeyCode) -> None:
        was_suppressed = self.suppress_toggle_keys
        self._check_toggle_combo(key, False)
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active and not was_suppressed and not self.suppress_toggle_keys:
            self.send({"v": 1, "type": "key", "key": self._key_name(key), "pressed": False})
        if not self.pressed_keys:
            self.suppress_toggle_keys = False

    async def handler(self, websocket: ServerConnection) -> None:
        with self.state_lock:
            self.tablet = websocket
            self.tablet_mode = False

        await websocket.send(json.dumps({
            "v": 1,
            "type": "hello",
            "name": self.name,
            "role": "controller",
            "width": self.width,
            "height": self.height,
            "toggle": "CTRL+ALT+SPACE",
        }, separators=(",", ":")))
        print(f"Android tablet connected: {websocket.remote_address}")
        print("Shortcut: Ctrl+Alt+Space toggles Ubuntu <-> Android")

        try:
            async for raw in websocket:
                try:
                    msg = json.loads(raw)
                except json.JSONDecodeError:
                    continue
                if msg.get("type") == "hello":
                    self.tablet_width = max(1, int(msg.get("width", 1080)))
                    self.tablet_height = max(1, int(msg.get("height", 2400)))
                    print(f"Tablet: {msg.get('name', 'ANDROID-TABLET')} {self.tablet_width}x{self.tablet_height}")
        except websockets.ConnectionClosed:
            pass
        finally:
            with self.state_lock:
                if self.tablet is websocket:
                    self.tablet = None
                    self.tablet_mode = False
            print("Android tablet disconnected")

    async def run_server(self) -> None:
        self.loop = asyncio.get_running_loop()
        async with serve(self.handler, self.host, self.port, ping_interval=5, ping_timeout=10, max_queue=256):
            print(f"NealLink Ubuntu -> Android listening on ws://{self.host}:{self.port}")
            print(f"Ubuntu X11 screen: {self.width}x{self.height}")
            print("Shortcut: Ctrl+Alt+Space = toggle Ubuntu ↔ Android")
            while not self.stop_event.is_set():
                await asyncio.sleep(0.25)

    def run(self) -> None:
        mouse_listener = mouse.Listener(on_move=self.on_move, on_click=self.on_click, on_scroll=self.on_scroll)
        key_listener = keyboard.Listener(on_press=self.on_press, on_release=self.on_release)
        mouse_listener.start()
        key_listener.start()
        try:
            asyncio.run(self.run_server())
        finally:
            mouse_listener.stop()
            key_listener.stop()


def main() -> None:
    parser = argparse.ArgumentParser(description="NealLink Ubuntu -> Android bridge")
    parser.add_argument("--config", default="config/ubuntu_android.json")
    args = parser.parse_args()
    UbuntuAndroidBridge(args.config).run()


if __name__ == "__main__":
    main()
