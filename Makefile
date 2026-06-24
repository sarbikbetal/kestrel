IMAGE ?= android-builder
CONTAINER_NAME ?= kestrel-builder-dev
GRADLE_CACHE ?= kestrel-gradle-cache
ANDROID_HOME_CACHE ?= kestrel-android-home
PROJECT_DIR := $(CURDIR)

.PHONY: help image up down build install release bundle test shell reset-container reset-caches clean-dev

help:
	@echo "Local development targets:"
	@echo "  make image            Build Docker builder image"
	@echo "  make up               Create/start persistent dev container"
	@echo "  make down             Stop persistent dev container"
	@echo "  make build            Build debug APK (no install)"
	@echo "  make install          Build + install debug APK to connected device"
	@echo "  make release          Build release APK"
	@echo "  make bundle           Build release AAB"
	@echo "  make test             Run unit tests in dev container"
	@echo "  make shell            Open shell in dev container"
	@echo "  make reset-container  Remove persistent dev container"
	@echo "  make reset-caches     Remove Docker cache volumes"
	@echo "  make clean-dev        Remove container + cache volumes"

image:
	docker build --platform linux/amd64 -t $(IMAGE) .

up:
	@existing_project_mount="$$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/project"}}{{.Source}}{{end}}{{end}}' $(CONTAINER_NAME) 2>/dev/null || true)"; \
	if [ -n "$$existing_project_mount" ] && [ "$$existing_project_mount" != "$(PROJECT_DIR)" ]; then \
		echo "▶ Recreating local builder container (project path changed)..."; \
		docker rm -f $(CONTAINER_NAME) >/dev/null; \
	fi; \
	if ! docker ps -a --format '{{.Names}}' | grep -Fxq $(CONTAINER_NAME); then \
		echo "▶ Creating persistent local builder container..."; \
		docker run -d --platform linux/amd64 --name $(CONTAINER_NAME) -w /project \
			-v "$(PROJECT_DIR):/project" \
			-v "$(GRADLE_CACHE):/root/.gradle" \
			-v "$(ANDROID_HOME_CACHE):/root/.android" \
			$(IMAGE) tail -f /dev/null >/dev/null; \
	elif ! docker ps --format '{{.Names}}' | grep -Fxq $(CONTAINER_NAME); then \
		echo "▶ Starting persistent local builder container..."; \
		docker start $(CONTAINER_NAME) >/dev/null; \
	fi

down:
	@docker stop $(CONTAINER_NAME) >/dev/null 2>&1 || true

build:
	./build.sh --build-only

install:
	./build.sh

release:
	./build.sh --release

bundle:
	./build.sh --bundle

test: up
	docker exec $(CONTAINER_NAME) ./gradlew test

shell: up
	docker exec -it $(CONTAINER_NAME) bash

reset-container:
	@docker rm -f $(CONTAINER_NAME) >/dev/null 2>&1 || true

reset-caches:
	@docker volume rm $(GRADLE_CACHE) $(ANDROID_HOME_CACHE) >/dev/null 2>&1 || true

clean-dev: reset-container reset-caches
