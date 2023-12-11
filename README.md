sftp-documents-provider
=======================

`SFTP Documents Provider` is an Android app to provide access to shared folder by SFTPS.

This is a proof of concept created in a few days. It works but be gentle.

Disclaimer 
----------

I am just a rookie, so please if you are an android expert please have a quick look at the bugs section as I need help with a few small issues :) 

Notes
-----

- Known issues have been filed as bugs, PR's are welcome :)
- Auth mode only supports private/public keys
  - Your private key must begin with `-----BEGIN RSA PRIVATE KEY----- `
  - OpenSSH keys are not supported
  - Add keys using the Overflow menu that (not sure why) shows up in the overflow menu on the toolbar at the bottom of screen (help!)
- The interface is not very polished

Features
--------
Does support
- multithreaded upload/download
- delete/remove
- remote copying
- move

Help wanted! :)
------------

- How do I notify the document client to refresh the contents of the server?
- How do I convert documentIds into URIS? Any minimal example would be welcome
  - Why are they needed?
- Why PDFs cannot be read? See bug https://github.com/aguaviva/sftp-documents-provider/issues/1
- How do I get random access to files? 

Pre-requisites
--------------

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
