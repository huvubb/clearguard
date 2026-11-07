// Dedicated command-service protocol.
package com.youlong.hd;
interface IClearGuardShell {
 IBinder start(in String[] command, in String[] environment, String directory, IBinder owner) = 0;
 int uid() = 1;
 void destroy() = 16777114;
}
