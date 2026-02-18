# chronos

# One time, globally
curl -s "https://get.sdkman.io" | bash
source ~/.zshrc

# Install GraalVM (native-image included)
sdk install java 21.0.2-graalce

# In your project
cd chronos
sdk env init        # creates .sdkmanrc
echo 'SDKMAN_AUTO_ENV=true' >> ~/.zshrc
source ~/.zshrc

# Confirm everything works
java -version
native-image --version

