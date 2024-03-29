sftp-documents-provider
=======================

`SFTP Documents Provider` is an Android app to provide access to a remote folder using SFTPS.

Disclaimer 
----------

This is a proof of concept created in a few days. It works but please be gentle. Also I am learning as I go, feedback is welcome ;) 

Features
--------

- file random access
- delete/remove
- remote copying (without a roundtrip)
- move

Notes
-----

- The interface is not very polished but PRs are welcome!!
- Remember to log in using the overflow menu
- Auth mode only supports private/public keys
  - Your private key must begin with `-----BEGIN RSA PRIVATE KEY----- `
  - OpenSSH keys are not supported
  - Add keys using the Overflow menu that (not sure why) shows up in the overflow menu on the toolbar at the bottom of screen (help!)
- Displaying thumbnails in remote connections might be very slow, disable this in your favourite file manager.     

Pre-requisites
--------------

- IDE: Android Studio Hedgehog | 2023.1.1  (This is what I used but prob not required)
- Android SDK 28
- Android Build Tools v28.0.3
- Android Support Repository
  
Credits
--------

- This project uses these great libs:
  - libssh2 
  - libgcrypt
  - libgpg-error
-  The document provider has been heavily based on [Google storage provider samples][1]

[1]: https://github.com/android/storage
