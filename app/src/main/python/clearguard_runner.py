import contextlib
import sys
from java import jclass


class Console:
    def write(self, text):
        jclass("java.lang.System").out.print(str(text))
        return len(text)

    def flush(self):
        jclass("java.lang.System").out.flush()


def run_source(source):
    sys.argv = ["<local-script>"]
    with contextlib.redirect_stdout(Console()), contextlib.redirect_stderr(Console()):
        exec(compile(source, "<local-script>", "exec"), {"__name__": "__main__"})
