# MiniVCS-A-Minimal-Git-Like-Version-Control-System-
# 🚀 MiniVCS – A Minimal Git-Like Version Control System (Java)

MiniVCS is a lightweight and educational version control system that mimics core Git functionality.  
It is built from scratch using Java 21 and Maven, with object storage, hashing, staging, commits, trees, branches, checkout, and more.

It is fully functional and can run globally on your system using the included installer.

---

## ✨ Features

- `init` — Initialize repository  
- `add` — Stage files  
- `commit` — Create commits  
- `status` — Show staged and modified files  
- `log` — View commit history  
- `checkout` — Restore any commit (detached HEAD)  
- `branch` — Create branches  
- `switch` — Switch branches  
- `show` — Display object/commit details  
- File storage using **SHA-1 blobs**  
- Commit metadata (author, timestamp, parent)  
- Fully object-based storage like Git (`blob`, `tree`, `commit`)  
- Installer for system-wide CLI usage  

---

## 📦 Installation

### 1. Download the release  
Go to **GitHub Releases** and download:

- `minivcs.jar`
- `install-minivcs.sh`

### 2. Run installer

```bash
chmod +x install-minivcs.sh
sudo ./install-minivcs.sh
