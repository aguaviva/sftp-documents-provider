package com.aguaviva.android.sftpstorageprovider;

public class Ssh2 {
    static {
        System.loadLibrary("ssh2_bindings");
    }

    public static final int LIBSSH2_FXF_READ    =      0x00000001;
    public static final int LIBSSH2_FXF_WRITE   =      0x00000002;
    public static final int LIBSSH2_FXF_APPEND  =      0x00000004;
    public static final int LIBSSH2_FXF_CREAT   =      0x00000008;
    public static final int LIBSSH2_FXF_TRUNC   =      0x00000010;
    public static final int LIBSSH2_FXF_EXCL    =      0x00000020;

    /* Read, write, execute/search by owner */
    public static final int LIBSSH2_SFTP_S_IRWXU =       0000700;     /* RWX mask for owner */
    public static final int LIBSSH2_SFTP_S_IRUSR =       0000400;     /* R for owner */
    public static final int LIBSSH2_SFTP_S_IWUSR =       0000200;     /* W for owner */
    public static final int LIBSSH2_SFTP_S_IXUSR =       0000100;     /* X for owner */

    /* Read, write, execute/search by group */
    public static final int LIBSSH2_SFTP_S_IRWXG =       0000070;     /* RWX mask for group */
    public static final int LIBSSH2_SFTP_S_IRGRP =       0000040;     /* R for group */
    public static final int LIBSSH2_SFTP_S_IWGRP =       0000020;     /* W for group */
    public static final int LIBSSH2_SFTP_S_IXGRP =       0000010;     /* X for group */

    /* Read, write, execute/search by others */
    public static final int LIBSSH2_SFTP_S_IRWXO =       0000007;     /* RWX mask for other */
    public static final int LIBSSH2_SFTP_S_IROTH =       0000004;     /* R for other */
    public static final int LIBSSH2_SFTP_S_IWOTH =       0000002;     /* W for other */
    public static final int LIBSSH2_SFTP_S_IXOTH =       0000001;     /* X for other */

    public static native int init_ssh();
    public static native void exit_ssh();

    public static native int session_connect(String hostname, int port);
    public static native int session_disconnect(int session_id);
    public static native int free(int session_id);
    public static native int session_set_blocking(int session_id, int blocking);
    public static native int session_get_blocking(int session_id);
    public static native String session_last_error(int session_id);
    public static native int session_last_errorno(int session_id);
    public static native String session_get_banner(int session_id);
    public static native byte[] get_host_key_hash(int session_id);
    public static native int session_auth(int session_id, String username, String pubkey, String privkey, String passphrase);
    public static native int receive(int session_id, String jscppath);

    //----------------------------
    public static native int sftp_init(int ssh2_session_id);
    public static native int sftp_shutdown(int ssh2_sftp_session);
    public static native int opendir(int sftp_session_id, String jscppath);
    public static native int closedir(int sftp_handle_id);
    public static native String readdir(int sftp_handle_id);

    public static native int openfile(int sftp_session_id, String jscppath, int creation_flags, int permissions_flags);
    public static native int closefile(int sftp_handle_id);
    public static native int readfile(int sftp_handle_id, byte[] data);
    public static native int writefile(int sftp_handle_id, byte[] data, int offset, int length);

    public static native String sftp_stat(int sftp_handle_id, String jscppath);

    public static native int rename(int sftp_session_id, String jscppath, String jscppath_new);

    public static native int exec(int sftp_session_id, String jcommand);

}
