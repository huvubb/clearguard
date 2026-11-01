import contextlib
import sys
import os
from java import jclass


class Console:
    def write(self, text):
        jclass("java.lang.System").out.print(str(text))
        return len(text)

    def flush(self):
        jclass("java.lang.System").out.flush()


def run_source(source, filename="<local-script>"):
    sys.argv = [filename]
    directory = os.path.dirname(filename)
    if directory and os.access(directory, os.R_OK | os.X_OK):
        sys.path.insert(0, directory)
        os.chdir(directory)
    with contextlib.redirect_stdout(Console()), contextlib.redirect_stderr(Console()):
        exec(compile(source.lstrip("\ufeff"), filename, "exec"), {"__name__": "__main__", "__file__": filename})
