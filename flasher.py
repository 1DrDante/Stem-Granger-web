"""Python equivalent of the RTGPS Serial Flash utility wrapper."""
from __future__ import annotations

import os
import subprocess
import threading
import time
from pathlib import Path
from typing import Callable, Optional


class Flasher:
    """Lightweight wrapper that launches the legacy FlashUtil executable."""

    def __init__(self, base_directory: Optional[os.PathLike[str]] = None) -> None:
        """Initialise the flasher and validate that the utility exists.

        Parameters
        ----------
        base_directory:
            Base directory that contains the ``flashStuff`` tree. When omitted we
            default to the directory that holds this module, mimicking the
            ``AppDomain.CurrentDomain.BaseDirectory`` behaviour in the original
            C# implementation.
        """

        if base_directory is None:
            base_directory = Path(__file__).resolve().parent
        else:
            base_directory = Path(base_directory)

        self.flash_util_path = (
            base_directory / "flashStuff" / "OLDflashUtil" / "FlashUtilCL.exe"
        )

        if not self.flash_util_path.exists():
            raise FileNotFoundError(
                f"Flash utility not found at: {self.flash_util_path}"
            )

    def flash_hex_async(
        self, hex_file_path: os.PathLike[str], on_complete: Optional[Callable[[bool], None]] = None
    ) -> threading.Thread:
        """Flash the provided HEX file on a background thread.

        Parameters
        ----------
        hex_file_path:
            Path to the HEX file that should be flashed.
        on_complete:
            Optional callback invoked with ``True`` on success or ``False`` on
            failure once the flashing process finishes.
        """

        hex_file_path = Path(hex_file_path)
        if not hex_file_path.exists():
            raise FileNotFoundError(f"HEX file not found at: {hex_file_path}")

        def _worker() -> None:
            success = False
            try:
                arguments = [
                    "downloadusb",
                    "-r",
                    str(hex_file_path),
                    "",
                    "0",
                    "1",
                ]

                process = subprocess.Popen(
                    [str(self.flash_util_path), *arguments],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                )

                while process.poll() is None:
                    print(".", end="", flush=True)
                    time.sleep(0.5)

                stdout, stderr = process.communicate()
                print("\nFlash Output:\n" + stdout)

                if process.returncode == 0:
                    success = True
                elif stderr:
                    print("Flash Error:\n" + stderr)
            except Exception as exc:  # pragma: no cover - defensive log path
                print(f"Exception during flash: {exc}")
            finally:
                if on_complete is not None:
                    try:
                        on_complete(success)
                    except Exception:
                        pass

        thread = threading.Thread(target=_worker, daemon=True)
        thread.start()
        return thread


__all__ = ["Flasher"]
