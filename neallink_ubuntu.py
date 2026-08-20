from __future__ import annotations

import argparse
import asyncio
import json
import subprocess
import threading
from pathlib import Path
from typing import Any

import websockets
from websockets.asyncio.server import ServerConnection, serve
from pynput import keyboard, mouse


class UbuntuAndroidBridge:
    def __init__(self, config_path: str) -> None:
        self.config = json.loads(Path(config_path).read_text(encoding="utf-8"))
        self.host = str(self.config.get("host", "0.0.0.0"))
        self.port = int(self.config.get("port", 24891))
        self.name = str(self.config.get("name", "NEAL-UBUNTU"))
        self.tablet = None
        self.loop: asyncio.AbstractEventLoop | None = None
        self.width, self.height = self._screen_size()
        self.x, self.y = self.width // 2, self.height // 2
        self.stop_event = threading.Event()

    @staticmethod
    def _screen_size() -> tuple[int, int]:
        try:
            out = subprocess.check_output(["xrandr", "--current"], text=True, stderr=subprocess.DEVNULL)
            for line in out.splitlines():
                if " connected " in line:
                    for token in line.split():
                        if "x" in token and "+" in token:
                            size = token.split("+")[0]
                            w, h = size.split("x", 1)
                            if w.isdigit() and h.isdigit():
                                return int(w), int(h)
        except Exception:
            pass
        return 1920, 1080

    async def handler(self, websocket: ServerConnection) -> None:
        self.tablet = websocket
        await websocket.send(json.dumps({
            "v": 1, "type": "hello", "name": self.name,
            "role": "controller", "width": self.width, "height": self.height,
        }, separators=(",", ":")))
        print(f"Android tablet connected: {websocket.remote_address}")
        try:
            async for raw in websocket:
                try:
                    msg = json.loads(raw)
                except json.JSONDecodeError:
                    continue
                if msg.get("type") == "hello":
                    await websocket.send(json.dumps({
                        "v": 1, "type": "cursor", "x": self.x, "y": self.y
                    }, separators=(",", ":")))
                    print(f"Tablet: {msg.get('name', 'ANDROID-TABLET')} {msg.get('width')}x{msg.get('height')}")
        except websockets.ConnectionClosed:
            pass
        finally:
            if self.tablet is websocket:
                self.tablet = None
            print("Android tablet disconnected")

    def send(self, payload: dict[str, Any]) -> None:
        if not self.loop or not self.tablet:
            return
        raw = json.dumps(payload, separators=(",", ":"))
        asyncio.run_coroutine_threadsafe(self.tablet.send(raw), self.loop)

    def on_move(self, x: int, y: int) -> None:
        dx, dy = x - self.x, y - self.y
        self.x, self.y = x, y
        if dx or dy:
            self.send({"v": 1, "type": "move", "dx": int(dx), "dy": int(dy)})

    def on_click(self, x: int, y: int, button: mouse.Button, pressed: bool) -> None:
        self.send({"v": 1, "type": "click", "button": getattr(button, "name", str(button)), "pressed": pressed})

    def on_scroll(self, x: int, y: int, dx: int, dy: int) -> None:
        self.send({"v": 1, "type": "scroll", "dx": int(dx), "dy": int(dy)})

    def on_press(self, key: keyboard.Key | keyboard.KeyCode) -> None:
        # Key events are forwarded for future Android input-method work.
        self.send({"v": 1, "type": "key", "key": self._key_name(key), "pressed": True})

    def on_release(self, key: keyboard.Key | keyboard.KeyCode) -> None:
        self.send({"v": 1, "type": "key", "key": self._key_name(key), "pressed": False})

    @staticmethod
    def _key_name(key: keyboard.Key | keyboard.KeyCode) -> str:
        if isinstance(key, keyboard.KeyCode):
            if key.char is not None:
                return f"char:{key.char}"
            if key.vk is not None:
                return f"vk:{key.vk}"
        return f"key:{getattr(key, 'name', repr(key))}"

    async def run_server(self) -> None:
        self.loop = asyncio.get_running_loop()
        async with serve(self.handler, self.host, self.port, ping_interval=5, ping_timeout=10):
            print(f"NealLink Ubuntu -> Android listening on ws://{self.host}:{self.port}")
            print(f"Ubuntu X11 screen: {self.width}x{self.height}")
            print(f"Tablet URL: ws://<UBUNTU-IP>:{self.port}")
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
