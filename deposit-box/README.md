# Deposit Box

This directory is shared between the host system and the Minecraft server container. You can use it to transfer files to and from the server.

Common uses:
- Transfer world backups
- Upload plugin JAR files
- Share configuration files
- Exchange data files

The directory is mounted at `/deposit-box` inside the container.