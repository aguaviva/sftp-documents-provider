package com.aguaviva.android.sftpstorageprovider;

import java.nio.ByteBuffer;

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

    public static native int init_ssh();
    public static native void exit_ssh();
    public static native int session_connect(String hostname, int port);
    public static native int session_disconnect(int session_id);
    public static native int session_set_blocking(int session_id, int blocking);
    public static native int session_get_blocking(int session_id);
    public static native String session_last_error(int session_id);
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

    public static native int openfile(int sftp_session_id, String jscppath, int flags);
    public static native int closefile(int sftp_handle_id);
    public static native int readfile(int sftp_handle_id, byte[] data);
    public static native int writefile(int sftp_handle_id, byte[] data, int length);

    public static native String get_permissions(int sftp_handle_id, String jscppath);

    public static native int rename(int sftp_session_id, String jscppath, String jscppath_new);

    public static native int exec(int sftp_session_id, String jcommand);

}
