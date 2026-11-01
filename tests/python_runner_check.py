"""Check the real bundled module's output bridge and script execution, without Android."""
import importlib.util
import io
from pathlib import Path
import sys
import types

captured = io.StringIO()
class Output:
    def print(self, text):
        captured.write(text)
    def flush(self):
        pass
sys.modules["java"] = types.SimpleNamespace(jclass=lambda name: types.SimpleNamespace(out=Output()))
source = Path(__file__).resolve().parents[1] / "app/src/main/python/clearguard_runner.py"
spec = importlib.util.spec_from_file_location("runner", source)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
runner.run_source("print('local-python-ok'); assert __name__ == '__main__'")
assert captured.getvalue() == "local-python-ok\n"
try:
    runner.run_source("raise ValueError('expected')")
    raise AssertionError("script error was lost")
except ValueError as error:
    assert str(error) == "expected"
print("PASS bundled Python execution and stdout bridge")
