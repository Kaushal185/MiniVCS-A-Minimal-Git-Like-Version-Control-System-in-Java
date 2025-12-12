#!/bin/bash

echo "Installing MiniVCS..."

# Install location for jar
INSTALL_DIR="/usr/local/lib/minivcs"

# Create directory
sudo mkdir -p $INSTALL_DIR

# Copy jar
sudo cp target/MiniVCS-1.0-SNAPSHOT-jar-with-dependencies.jar $INSTALL_DIR/minivcs.jar

# Create launcher script
sudo bash -c 'cat > /usr/local/bin/minivcs << "EOF"
#!/bin/bash
java -jar /usr/local/lib/minivcs/minivcs.jar "$@"
EOF'

# Make launcher executable
sudo chmod +x /usr/local/bin/minivcs

echo "MiniVCS installed successfully!"
echo "Try running: minivcs init"
