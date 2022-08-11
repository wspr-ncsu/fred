# Make sure some file function is even in this executable.
file_functions = ["fopen", "fopen_s", "freopen", "freopen_s", "rename", "tmpfile", "write", "read", "open", "openat", "open64",
                  "openat64", "__open_real", "__openat_real", "__open_2", "__openat_2", "fdopen", "fopen64", "freopen64",
                  "tmpfile64", "renameat", "tempnam", "tmpnam", "fprintf", "vfprintf", "vdprintf", "fscanf", "mkdir", "umask",
                  "__umask_chk", "__umask_real", "chmod", "fstat", "fchmod", "fstat64", "fstatat", "fstatat64", "lstat",
                  "lstat64", "stat", "stat64", "mknod", "mkfifo", "statx", "utimensat", "futimens", "mknodat", "mkdirat",
                  "fchmodat", "mkfifoat", "fcntl", "fcntl64", "jniCreateFileDescriptor", "jniGetFDFromFileDescriptor",
                  "jniSetFileDescriptorOfFD", "execve", "unlink", "unlinkat", "link", "linkat", "symlink", "symlinkat",
                  "chown", "fchown", "lchown", "fchownat", "readlink", "readlinkat", "rmdir", "mkstemp", "mktemp", "mkdtemp",
                  "dlopen", "__loader_dlopen", "dlsym", "__loader_dlsym", "android_dlopen_ext", "__loader_android_dlopen_ext",
                  "WriteStringToFile", "WriteStringToFd", "ReadFileToString", "access", "faccessat", "realpath", "basename",
                  "dirname", "readdir", "opendir"]
