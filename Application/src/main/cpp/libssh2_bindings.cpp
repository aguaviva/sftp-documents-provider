//#include <libssh2_setup.h>
#include <jni.h>
#include <android/log.h>
#include <inttypes.h>

#define TAG "MY_APP"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,    TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,    TAG, __VA_ARGS__)

#ifdef HAVE_SYS_SOCKET_H
#include <sys/socket.h>
#endif
#ifdef HAVE_UNISTD_H
#include <unistd.h>
#endif
#ifdef HAVE_NETINET_IN_H
#include <netinet/in.h>
#endif
#ifdef HAVE_ARPA_INET_H
#include <arpa/inet.h>
#endif


#include <libssh2.h>
#include <libssh2_sftp.h>

#ifdef WIN32
#define write(f, b, c)  write((f), (b), (unsigned int)(c))
#endif

#include <sys/socket.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <stdio.h>
#include <string.h>
#include <android/log.h>
#include <map>

////////////////////////////////////////////////////////////////////
template <typename T> class Database {
    std::map<int, T> database;
    uint32_t last_id = 0;
public:
    Database(int starting_id)
    {
        last_id = starting_id;
    }
    uint32_t add(T t)
    {
        last_id++;
        database[last_id] = t;
        return last_id;
    }

    bool del(uint32_t id)
    {
        auto it1 = database.find(id);
        if (it1 == database.end()) {
            return false;
        }

        database.erase(it1);

        return true;
    }

    bool get(uint32_t id, T* entry_out)
    {
        auto it1 = database.find(id);
        if (it1 == database.end()) {
            return false;
        }

        *entry_out = it1->second;

        return true;
    }
};

struct Ssh2_session {
    LIBSSH2_SESSION *m_pSession = nullptr;
    libssh2_socket_t m_sock = 0;
};

Database<Ssh2_session> database_sessions(1);
Database<LIBSSH2_SFTP *> database_sftp_session(1000);
Database<LIBSSH2_SFTP_HANDLE *> database_sft_handle(2000);

extern "C" JNIEXPORT jint JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_init_1ssh(JNIEnv * env, jclass obj) {
    int rc = libssh2_init(0);
    if (rc) {
        fprintf(stderr, "libssh2 initialization failed (%d)\n", rc);
        return rc;
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_exit_1ssh(JNIEnv * env, jclass obj) {
    libssh2_exit();
}
/*
void my_libssh2_trace_handler_func(LIBSSH2_SESSION *session,
                                           void* context,
                                           const char *data,
                                           size_t length)
{
    LOGI("%s", data);
}
*/

extern "C" JNIEXPORT jint JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_session_1connect(JNIEnv * env, jclass obj, jstring jhostname, int port) {
    uint32_t hostaddr;
    libssh2_socket_t sock;
    struct sockaddr_in sin;
    int rc;
    LIBSSH2_SESSION *session = NULL;

    /* Ultra basic "connect to port 22 on localhost".  Your code is
     * responsible for creating the socket establishing the connection
     */
    sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock == LIBSSH2_INVALID_SOCKET) {
        fprintf(stderr, "failed to create socket.\n");
        return -2;
    }

    const char *hostname = env->GetStringUTFChars(jhostname, NULL);
    hostaddr = inet_addr(hostname);

    sin.sin_family = AF_INET;
    sin.sin_port = htons(port);
    sin.sin_addr.s_addr = hostaddr;
    if (connect(sock, (struct sockaddr *) (&sin), sizeof(struct sockaddr_in))) {
        fprintf(stderr, "failed to connect.\n");

        env->ReleaseStringUTFChars(jhostname, hostname);

        return -3;
    }

    env->ReleaseStringUTFChars(jhostname, hostname);

    /* Create a session instance */
    session = libssh2_session_init();
    if (!session) {
        fprintf(stderr, "Could not initialize SSH session.\n");
        return -4;
    }
/*
    libssh2_trace(session, LIBSSH2_TRACE_SFTP );

    rc = libssh2_trace_sethandler(session,
                                  nullptr,
                                  my_libssh2_trace_handler_func);

    if(rc) {
        fprintf(stderr, "Failure libssh2_trace_sethandler : %d\n", rc);
        return -5;
    }
    */
    rc = libssh2_session_handshake(session, sock);
    if(rc) {
        fprintf(stderr, "Failure establishing SSH session: %d\n", rc);
        return -5;
    }

    Ssh2_session s = {session, sock};
    return database_sessions.add(s);
}

extern "C" JNIEXPORT jint JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_session_1disconnect(JNIEnv * env, jclass obj, int ssh2_session_id) {

    Ssh2_session s;
    if (database_sessions.get(ssh2_session_id, &s)==false) {
        return -1;
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    if(session) {
        libssh2_session_disconnect(session, "Normal Shutdown");
        libssh2_session_free(session);
    }

    if(sock != LIBSSH2_INVALID_SOCKET) {
        shutdown(sock, 2);
#ifdef WIN32
        closesocket(sock);
#else
        close(sock);
#endif
    }

    database_sessions.del(ssh2_session_id);

    return 0;
}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_session_1set_1blocking(JNIEnv * env, jclass obj, int ssh2_session_id, int blocking) {
    Ssh2_session s;
    if (database_sessions.get(ssh2_session_id, &s)==false) {
        return -1;
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    if(session) {
        libssh2_session_set_blocking(session, blocking);
        return 0;
    }
    return -2;
}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_session_1get_1blocking(JNIEnv * env, jclass obj, int ssh2_session_id) {
    Ssh2_session s;
    if (database_sessions.get(ssh2_session_id, &s)==false) {
        return -1;
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    if(session) {
        return libssh2_session_get_blocking(session);
    }
    return -2;
}


extern "C" JNIEXPORT jstring JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_session_1get_1banner(JNIEnv * env, jclass obj, int ssh2_session_id) {

    Ssh2_session s;
    if (database_sessions.get(ssh2_session_id, &s)==false) {
        return env->NewStringUTF("");
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    const char *pBanner = libssh2_session_banner_get(session);
    //LOGE("%s", pBanner);
    return env->NewStringUTF(pBanner);
}
extern "C" JNIEXPORT jbyteArray JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_get_1host_1key_1hash(JNIEnv * env, jclass obj, int session_id) {

    Ssh2_session s;
    if (database_sessions.get(session_id, &s)==false) {
        return env->NewByteArray(0);
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    const char *fingerprint = libssh2_hostkey_hash(session, LIBSSH2_HOSTKEY_HASH_SHA256);

    jbyteArray ret = env->NewByteArray(32);
    env->SetByteArrayRegion(ret,0,32, (const jbyte*)fingerprint);
    return ret;
}

extern "C" JNIEXPORT jint JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_session_1auth(JNIEnv * env, jclass obj, int session_id, jstring jusername, jstring jpubkey, jstring jprivkey, jstring jpassphrase) {

    Ssh2_session s;
    if (database_sessions.get(session_id, &s)==false) {
        return -1;
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    const char *username = env->GetStringUTFChars(jusername, NULL);
    const char *pubkey = env->GetStringUTFChars(jpubkey, NULL);
    const char *privkey = env->GetStringUTFChars(jprivkey, NULL);
    const char *passphrase = env->GetStringUTFChars(jpassphrase, NULL);

    char *pList = libssh2_userauth_list(session, username, strlen(username));
    LOGI("libssh2_userauth_list %s", pList);

    int res = libssh2_userauth_publickey_fromfile(session,
                                                    username,
                                                    pubkey,
                                                    privkey,
                                                    passphrase);

    env->ReleaseStringUTFChars(jusername, username);
    env->ReleaseStringUTFChars(jpubkey, pubkey);
    env->ReleaseStringUTFChars(jprivkey, privkey);
    env->ReleaseStringUTFChars(jpassphrase, passphrase);

    return res;
}

extern "C" JNIEXPORT jstring JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_session_1last_1error(JNIEnv * env, jclass obj, int session_id) {

    Ssh2_session s;
    if (database_sessions.get(session_id, &s)==false) {
        return env->NewStringUTF("Unknown session");
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    char *pBuff;
    libssh2_session_last_error(session, &pBuff, NULL, 0);
    return env->NewStringUTF(pBuff);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_aguaviva_android_sftpstorageprovider_Ssh2_exec(JNIEnv *env, jclass clazz, jint ssh2_session_id,
                                                   jstring jcommand) {

    Ssh2_session s;
    if (database_sessions.get(ssh2_session_id, &s)==false) {
        return -1;
    }

    int res=0;
    LIBSSH2_CHANNEL  *channel = libssh2_channel_open_session(s.m_pSession);
    if(channel) {
        const char *command = env->GetStringUTFChars(jcommand, NULL);

        res =libssh2_channel_exec(channel, command);

        env->ReleaseStringUTFChars(jcommand, command);
        if(res) {
            LOGE("Unable to request command on channel");
        }

    }
    else
    {
        LOGE("Unable to libssh2_channel_open_session");
    }

    return res;

}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_receive(JNIEnv * env, jclass obj, int session_id, jstring jscppath) {

    Ssh2_session s;
    if (database_sessions.get(session_id, &s)==false) {
        return -1;
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    LIBSSH2_CHANNEL *channel;
    libssh2_struct_stat fileinfo;
    libssh2_struct_stat_size got = 0;

    const char *scppath = env->GetStringUTFChars(jscppath, NULL);

    /* Request a file via SCP */
    channel = libssh2_scp_recv2(session, scppath, &fileinfo);

    env->ReleaseStringUTFChars(jscppath, scppath);

    if (!channel) {
        fprintf(stderr, "Unable to open a session: %d\n",
                libssh2_session_last_errno(session));

        return -1;
    }

    while (got < fileinfo.st_size) {
        char mem[1024];
        int amount = sizeof(mem);
        ssize_t nread;

        if ((fileinfo.st_size - got) < amount) {
            amount = (int) (fileinfo.st_size - got);
        }

        nread = libssh2_channel_read(channel, mem, amount);

        if (nread > 0) {
            write(1, mem, nread);
        } else if (nread < 0) {
            LOGE("libssh2_channel_read() failed: %d\n",

                 (int) nread);
            break;
        }
        got += nread;
    }

    libssh2_channel_free(channel);

    channel = NULL;
    return 0;
}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_sftp_1init(JNIEnv * env, jclass obj, int session_id) {

    Ssh2_session s;
    if (database_sessions.get(session_id, &s)==false) {
        return -1;
    }

    LIBSSH2_SESSION *session = s.m_pSession;
    libssh2_socket_t sock = s.m_sock;

    LIBSSH2_SFTP * sftp_session = libssh2_sftp_init(session);

    return database_sftp_session.add(sftp_session);

}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_sftp_1shutdown(JNIEnv * env, jclass obj, int sftp_session_id) {

    LIBSSH2_SFTP *sftp_session;
    if (database_sftp_session.get(sftp_session_id, &sftp_session)==false) {
        return -1;
    }

    libssh2_sftp_shutdown(sftp_session);

    database_sftp_session.del(sftp_session_id);
    return 0;
}



extern "C"
JNIEXPORT int JNICALL
Java_com_aguaviva_android_sftpstorageprovider_Ssh2_rename(JNIEnv *env, jclass clazz, jint sftp_session_id,
                                                     jstring jscppath, jstring jscppath_new) {
    LIBSSH2_SFTP *sftp_session;
    if (database_sftp_session.get(sftp_session_id, &sftp_session)==false) {
        return -1;
    }

    const char *source_filename = env->GetStringUTFChars(jscppath, NULL);
    const char *destination_filename = env->GetStringUTFChars(jscppath_new, NULL);

    int res = libssh2_sftp_rename(sftp_session, source_filename, destination_filename);

    env->ReleaseStringUTFChars(jscppath, source_filename);
    env->ReleaseStringUTFChars(jscppath_new, destination_filename );

    return res;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_aguaviva_android_sftpstorageprovider_Ssh2_get_1permissions(JNIEnv *env, jclass clazz,
                                                               jint sftp_session_id,
                                                               jstring jsftppath ) {
    LIBSSH2_SFTP *sftp_session;
    if (database_sftp_session.get(sftp_session_id, &sftp_session) == false) {
        return env->NewStringUTF("* id not found");
    }

    const char *sftppath = env->GetStringUTFChars(jsftppath, NULL);

    LIBSSH2_SFTP_ATTRIBUTES attrs;
    int res = libssh2_sftp_stat(sftp_session, sftppath, &attrs);
    if (res<0) {
        return env->NewStringUTF("* libssh2_sftp_stat err");
    }

    env->ReleaseStringUTFChars(jsftppath, sftppath);

    char permissions[256];

    strcpy(permissions, "----------");

    if (LIBSSH2_SFTP_S_ISDIR(attrs.permissions))
        permissions[0]='d';

    if (attrs.permissions & LIBSSH2_SFTP_S_IRUSR)
        permissions[1]='r';
    if (attrs.permissions & LIBSSH2_SFTP_S_IWUSR)
        permissions[2]='w';
    if (attrs.permissions & LIBSSH2_SFTP_S_IXUSR)
        permissions[3]='x';

    if (attrs.permissions & LIBSSH2_SFTP_S_IRGRP)
        permissions[4]='r';
    if (attrs.permissions & LIBSSH2_SFTP_S_IWGRP)
        permissions[5]='w';
    if (attrs.permissions & LIBSSH2_SFTP_S_IXGRP)
        permissions[6]='x';


    if (attrs.permissions & LIBSSH2_SFTP_S_IROTH)
        permissions[7]='r';
    if (attrs.permissions & LIBSSH2_SFTP_S_IWOTH)
        permissions[8]='w';
    if (attrs.permissions & LIBSSH2_SFTP_S_IXOTH)
        permissions[9]='x';

    permissions[10]=' ';

    sprintf(&permissions[11],"%ld %llu", attrs.atime, attrs.filesize);

    return env->NewStringUTF(permissions);
}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_opendir(JNIEnv * env, jclass obj, int sftp_session_id, jstring jsftppath) {

    LIBSSH2_SFTP *sftp_session;
    if (database_sftp_session.get(sftp_session_id, &sftp_session)==false) {
        return -1;
    }

    const char *sftppath = env->GetStringUTFChars(jsftppath, NULL);

    LIBSSH2_SFTP_HANDLE *sftp_handle = libssh2_sftp_opendir(sftp_session, sftppath);

    env->ReleaseStringUTFChars(jsftppath, sftppath);

    return database_sft_handle.add(sftp_handle);

}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_closedir(JNIEnv * env, jclass obj, int sftp_handle_id) {

    LIBSSH2_SFTP_HANDLE *sftp_handle;
    if (database_sft_handle.get(sftp_handle_id, &sftp_handle)==false) {
        return -1;
    }

    libssh2_sftp_closedir(sftp_handle);

    database_sft_handle.del(sftp_handle_id);
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_readdir(JNIEnv * env, jclass obj, int sftp_handle_id) {

    LIBSSH2_SFTP_HANDLE *sftp_handle;
    if (database_sft_handle.get(sftp_handle_id, &sftp_handle) == false) {
        return env->NewStringUTF("");
    }

    char mem[512];
    char longentry[512];
    LIBSSH2_SFTP_ATTRIBUTES attrs;

    /* loop until we fail */
    int rc = libssh2_sftp_readdir_ex(sftp_handle, mem, sizeof(mem),
                                     longentry, sizeof(longentry), &attrs);
    if (rc > 0) {
        /* rc is the length of the file name in the mem
           buffer */

        printf("%s\n", longentry);
        return env->NewStringUTF(longentry);
    }

    return env->NewStringUTF("");
}


extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_openfile(JNIEnv * env, jclass obj, int sftp_session_id, jstring jsftppath, int creation_flags, int permissions_flags) {

    LIBSSH2_SFTP *sftp_session;
    if (database_sftp_session.get(sftp_session_id, &sftp_session)==false) {
        return -1;
    }

    const char *sftppath = env->GetStringUTFChars(jsftppath, NULL);

    LIBSSH2_SFTP_HANDLE *sftp_handle = libssh2_sftp_open(sftp_session, sftppath, creation_flags, permissions_flags);

    env->ReleaseStringUTFChars(jsftppath, sftppath);

    return database_sft_handle.add(sftp_handle);
}

extern "C" JNIEXPORT int JNICALL  Java_com_aguaviva_android_sftpstorageprovider_Ssh2_closefile(JNIEnv * env, jclass obj, int sftp_handle_id) {

    LIBSSH2_SFTP_HANDLE *sftp_handle;
    if (database_sft_handle.get(sftp_handle_id, &sftp_handle)==false) {
        return -1;
    }

    libssh2_sftp_close(sftp_handle);

    database_sft_handle.del(sftp_handle_id);
    return 0;
}

extern "C" JNIEXPORT int JNICALL Java_com_aguaviva_android_sftpstorageprovider_Ssh2_readfile(JNIEnv *env, jclass clazz, jint sftp_handle_id, jbyteArray data) {

    LIBSSH2_SFTP_HANDLE *sftp_handle;
    if (database_sft_handle.get(sftp_handle_id, &sftp_handle) == false) {
        return 0;
    }

    jbyte *buffer = env->GetByteArrayElements(data, NULL);
    int len = env->GetArrayLength(  data );
    ssize_t s = libssh2_sftp_read(sftp_handle, (char *)buffer, len);

    env->ReleaseByteArrayElements(data, buffer, 0);

    return s;
}


extern "C" JNIEXPORT int JNICALL Java_com_aguaviva_android_sftpstorageprovider_Ssh2_writefile(JNIEnv *env, jclass clazz, jint sftp_handle_id, jbyteArray data, int offset, int length) {

    LIBSSH2_SFTP_HANDLE *sftp_handle;
    if (database_sft_handle.get(sftp_handle_id, &sftp_handle) == false) {
        return 0;
    }

    jbyte *buffer = env->GetByteArrayElements(data, NULL);
    int len = env->GetArrayLength(  data );
    ssize_t s = libssh2_sftp_write(sftp_handle, ((char *)buffer)+offset, length);

    env->ReleaseByteArrayElements(data, buffer, 0);

    return s;
}




