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
from websockets.asyncio.server import ServerConnection, serve
from pynput import keyboard, mouse


class UbuntuAndroidBridge:
    """Owns the physical mouse on Ubuntu and hands it to Android at the right edge."""

    def __init__(self, config_path: str) -> None:
        self.config = json.loads(Path(config_path).read_text(encoding="utf-8"))
        self.host = str(self.config.get("host", "0.0.0.0"))
        self.port = int(self.config.get("port", 24891))
        self.name = str(self.config.get("name", "NEAL-UBUNTU"))
        self.edge_margin = max(2, int(self.config.get("edge_margin", 6)))
        self.capture_center_ratio = float(self.config.get("capture_center_ratio", 0.5))

        self.tablet: ServerConnection | None = None
        self.loop: asyncio.AbstractEventLoop | None = None
        self.stop_event = threading.Event()
        self.state_lock = threading.RLock()

        self.width, self.height = self._screen_size()
        self.center_x = int(self.width * self.capture_center_ratio)
        self.center_y = self.height // 2
        self.mouse_x = self.width // 2
        self.mouse_y = self.height // 2

        self.tablet_width = 1080
        self.tablet_height = 2400
        self.tablet_x = 0
        self.tablet_y = self.tablet_height // 2
        self.tablet_mode = False
        self.ignore_mouse_until = 0.0

    @staticmethod
    def _screen_size() -> tuple[int, int]:
        try:
            out = subprocess.check_output(
                ["xrandr", "--current"], text=True, stderr=subprocess.DEVNULL
            )
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
        """Re-center the real Ubuntu pointer so the physical mouse can keep moving."""
        self.ignore_mouse_until = time.monotonic() + 0.08
        try:
            subprocess.run(
                ["xdotool", "mousemove", "--sync", str(int(x)), str(int(y))],
                check=False,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except FileNotFoundError:
            print("ERROR: xdotool is required. Install it with: sudo apt install xdotool")

    def _send_now(self, payload: dict[str, Any]) -> None:
        if not self.loop or not self.tablet:
            return
        raw = json.dumps(payload, separators=(",", ":"))
        asyncio.run_coroutine_threadsafe(self.tablet.send(raw), self.loop)

    def send(self, payload: dict[str, Any]) -> None:
        with self.state_lock:
            if not self.tablet:
                return
        self._send_now(payload)

    def _enter_tablet_mode(self) -> None:
        with self.state_lock:
            if self.tablet_mode or not self.tablet:
                return
            self.tablet_mode = True
            self.tablet_x = 8
            self.tablet_y = int(
                self.mouse_y / max(1, self.height - 1) * max(1, self.tablet_height - 1)
            )
            start_x = self.tablet_x
            start_y = self.tablet_y

        self.send(
            {
                "v": 1,
                "type": "handoff",
                "side": "right",
                "x": start_x,
                "y": start_y,
                "width": self.tablet_width,
                "height": self.tablet_height,
            }
        )
        self._warp_mouse(self.center_x, self.center_y)
        print("Mouse ownership -> Android tablet")

    def _leave_tablet_mode(self) -> None:
        with self.state_lock:
            if not self.tablet_mode:
                return
            self.tablet_mode = False
            host_y = int(
                self.tablet_y / max(1, self.tablet_height - 1) * max(1, self.height - 1)
            )
            host_y = max(0, min(self.height - 1, host_y))
            host_x = max(0, self.width - self.edge_margin - 1)

        self._warp_mouse(host_x, host_y)
        self.mouse_x, self.mouse_y = host_x, host_y
        self.send({"v": 1, "type": "host_mode"})
        print("Mouse ownership -> Ubuntu")

    async def handler(self, websocket: ServerConnection) -> None:
        # One tablet at a time for this MVP.
        with self.state_lock:
            self.tablet = websocket
            self.tablet_mode = False

        await websocket.send(
            json.dumps(
                {
                    "v": 1,
                    "type": "hello",
                    "name": self.name,
                    "role": "controller",
                    "width": self.width,
                    "height": self.height,
                    "edge": "right",
                },
                separators=(",", ":"),
            )
        )
        print(f"Android tablet connected: {websocket.remote_address}")

        try:
            async for raw in websocket:
                try:
                    msg = json.loads(raw)
                except json.JSONDecodeError:
                    continue

                msg_type = msg.get("type")
                if msg_type == "hello":
                    self.tablet_width = max(1, int(msg.get("width", 1080)))
                    self.tablet_height = max(1, int(msg.get("height", 2400)))
                    print(
                        f"Tablet: {msg.get('name', 'ANDROID-TABLET')} "
                        f"{self.tablet_width}x{self.tablet_height}"
                    )
                elif msg_type == "return_host":
                    self._leave_tablet_mode()
                elif msg_type == "keepalive":
                    await websocket.send(json.dumps({"v": 1, "type": "ack"}))
        except websockets.ConnectionClosed:
            pass
        finally:
            with self.state_lock:
                was_active = self.tablet is websocket
                if was_active:
                    self.tablet = None
                    self.tablet_mode = False
            if was_active:
                print("Android tablet disconnected")

    def on_move(self, x: int, y: int) -> None:
        now = time.monotonic()
        if now < self.ignore_mouse_until:
            self.mouse_x, self.mouse_y = x, y
            return

        with self.state_lock:
            tablet_mode = self.tablet_mode
            tablet_connected = self.tablet is not None

        if not tablet_connected:
            self.mouse_x, self.mouse_y = x, y
            return

        if not tablet_mode:
            self.mouse_x, self.mouse_y = x, y
            if x >= self.width - self.edge_margin:
                self._enter_tablet_mode()
            return

        # Tablet owns the pointer. Convert absolute Ubuntu movement around the
        # recenter point into relative deltas, then recenter immediately.
        dx = x - self.center_x
        dy = y - self.center_y
        if dx == 0 and dy == 0:
            return

        with self.state_lock:
            self.tablet_x = max(0, min(self.tablet_width - 1, self.tablet_x + dx))
            self.tablet_y = max(0, min(self.tablet_height - 1, self.tablet_y + dy))
            next_x, next_y = self.tablet_x, self.tablet_y

        self.send({"v": 1, "type": "move", "dx": int(dx), "dy": int(dy), "x": next_x, "y": next_y})
        self._warp_mouse(self.center_x, self.center_y)

    def on_click(self, x: int, y: int, button: mouse.Button, pressed: bool) -> None:
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active:
            self.send(
                {
                    "v": 1,
                    "type": "click",
                    "button": getattr(button, "name", str(button)),
                    "pressed": bool(pressed),
                }
            )

    def on_scroll(self, x: int, y: int, dx: int, dy: int) -> None:
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active and (dx or dy):
            self.send({"v": 1, "type": "scroll", "dx": int(dx), "dy": int(dy)})

    @staticmethod
    def _key_name(key: keyboard.Key | keyboard.KeyCode) -> str:
        if isinstance(key, keyboard.KeyCode):
            if key.char is not None:
                return f"char:{key.char}"
            if key.vk is not None:
                return f"vk:{key.vk}"
        return f"key:{getattr(key, 'name', repr(key))}"

    def on_press(self, key: keyboard.Key | keyboard.KeyCode) -> None:
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active:
            self.send({"v": 1, "type": "key", "key": self._key_name(key), "pressed": True})

    def on_release(self, key: keyboard.Key | keyboard.KeyCode) -> None:
        with self.state_lock:
            active = self.tablet_mode and self.tablet is not None
        if active:
            self.send({"v": 1, "type": "key", "key": self._key_name(key), "pressed": False})

    async def run_server(self) -> None:
        self.loop = asyncio.get_running_loop()
        async with serve(
            self.handler,
            self.host,
            self.port,
            ping_interval=5,
            ping_timeout=10,
            max_queue=256,
        ):
            print(f"NealLink Ubuntu -> Android listening on ws://{self.host}:{self.port}")
            print(f"Ubuntu X11 screen: {self.width}x{self.height}")
            print("Move the mouse to the RIGHT EDGE to hand it to Android.")
            print("Move the tablet cursor to its LEFT EDGE to return to Ubuntu.")
            while not self.stop_event.is_set():
                await asyncio.sleep(0.25)

    def run(self) -> None:
        mouse_listener = mouse.Listener(
            on_move=self.on_move,
            on_click=self.on_click,
            on_scroll=self.on_scroll,
        )
        key_listener = keyboard.Listener(
            on_press=self.on_press,
            on_release=self.on_release,
        )
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
