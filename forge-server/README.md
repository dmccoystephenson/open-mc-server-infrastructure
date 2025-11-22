# Forge Server Files Directory

This directory is for user-provided Forge/ATM10 server files.

## Required for Forge Server

If you want to run a Forge server with All the Mods 10 (ATM10), you must:

1. **Download ATM10 Server Files**:
   - Visit: https://www.curseforge.com/minecraft/modpacks/all-the-mods-10/files
   - Download the **Server Files** (not the client modpack)

2. **Place the Downloaded File Here**:
   ```bash
   # Rename the downloaded file to atm10-server.zip
   mv ~/Downloads/Server-Files-*.zip forge-server/atm10-server.zip
   ```

3. **Expected File Structure**:
   ```
   forge-server/
     └── atm10-server.zip    <- Place your downloaded server files here
   ```

## Why Manual Download?

CurseForge does not allow automated downloads of modpack files. You must manually download the server files from their website.

## Building the Server

Once you've placed the `atm10-server.zip` file in this directory:

1. Set `SERVER_TYPE=forge` in your `.env` file
2. Run `./up.sh` to build and start the server

For more information, see the main README.md file.
