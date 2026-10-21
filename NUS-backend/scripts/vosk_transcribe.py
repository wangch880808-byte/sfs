#!/usr/bin/env python3
import argparse
import json
import os
import sys
import wave
from pathlib import Path

from vosk import KaldiRecognizer, Model


def read_transcript(model_dir: str, input_path: str) -> str:
    wav_path = Path(input_path)
    if not wav_path.exists():
        raise FileNotFoundError(f"audio not found: {input_path}")

    wf = wave.open(str(wav_path), "rb")
    try:
        if wf.getnchannels() != 1 or wf.getsampwidth() != 2:
            raise ValueError("audio must be mono PCM16 wav")
        model = Model(model_dir)
        recognizer = KaldiRecognizer(model, wf.getframerate())
        recognizer.SetWords(True)
        parts: list[str] = []
        while True:
            data = wf.readframes(4000)
            if len(data) == 0:
                break
            if recognizer.AcceptWaveform(data):
                result = json.loads(recognizer.Result())
                text = str(result.get("text") or "").strip()
                if text:
                    parts.append(text)
        final_result = json.loads(recognizer.FinalResult())
        final_text = str(final_result.get("text") or "").strip()
        if final_text:
            parts.append(final_text)
        return "\n".join(parts).strip()
    finally:
        wf.close()


def main() -> int:
    parser = argparse.ArgumentParser(description="Transcribe wav audio via local Vosk model")
    parser.add_argument("input", help="wav audio path")
    parser.add_argument(
        "--model-dir",
        default=os.environ.get("NUS_VOSK_MODEL_DIR", "/opt/nus/models/vosk-model-small-cn-0.22"),
        help="local vosk model directory",
    )
    args = parser.parse_args()

    transcript = read_transcript(args.model_dir, args.input)
    sys.stdout.write(transcript)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
