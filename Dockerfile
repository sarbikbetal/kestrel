FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends \
    wget unzip curl git \
    && rm -rf /var/lib/apt/lists/*

# Install Android command-line tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
         -O /tmp/cmdtools.zip && \
    unzip -q /tmp/cmdtools.zip -d /tmp && \
    mv /tmp/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm /tmp/cmdtools.zip

# Accept licenses and install SDK packages
RUN yes | sdkmanager --licenses > /dev/null && \
    sdkmanager \
        "platforms;android-37.0" \
        "build-tools;37.0.0" \
        "platform-tools"

WORKDIR /project
