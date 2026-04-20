# Audiobook Library

A robust Java-based application designed to manage personal audiobook collections by generating a clean, intuitive web interface and providing functionality for ZIP downloads. This project is optimized for deployment behind an **Nginx** reverse proxy.

## 🚀 Features

* **Web Interface Generation:** Automatically transforms your audio folder structure into a navigable web library.
* **ZIP Packaging:** Allows users to download entire audiobooks as a single compressed ZIP file.
* **Nginx Integration:** Pre-configured for high-performance static file serving and traffic management.
* **Lightweight & Efficient:** Built with Java focusing on optimized I/O operations for large audio files.

## 🛠️ Tech Stack

* **Language:** Java (JDK 17+)
* **Build Tool:** Maven/Gradle
* **Web Server:** Nginx
* **Deployment:** Compatible with Windows and Linux (Perfect for Raspberry Pi home servers).

## 📂 Project Structure

To ensure the application processes your library correctly, organize your files as follows:

```text
C:\projetos\audiobook-library
├── src/                # Java source code
├── web/                # HTML/CSS templates and assets
├── storage/            # Your .mp3 / .m4b / .m4a files
└── nginx/              # Sample configuration files