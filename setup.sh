
#!/usr/bin/env bash
set -euo pipefail

echo "===================================="
echo "       YmcChat Android Setup"
echo "===================================="

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

ANDROID_DIR="android/app"
JAVA_DIR="$ANDROID_DIR/src/main/java/com/ymcchat/app"
MANIFEST="$ANDROID_DIR/src/main/AndroidManifest.xml"
GRADLE="$ANDROID_DIR/build.gradle.kts"

# Vérification du projet
if [ ! -f "$GRADLE" ]; then
    echo "ERREUR : $GRADLE introuvable."
    exit 1
fi

if [ ! -f "$MANIFEST" ]; then
    echo "ERREUR : AndroidManifest.xml introuvable."
    exit 1
fi

echo "[1/6] Création des dossiers..."

mkdir -p "$JAVA_DIR/network"
mkdir -p "$JAVA_DIR/data/auth"
mkdir -p "$JAVA_DIR/repository"

echo "[2/6] Création de ApiConfig.kt..."

cat > "$JAVA_DIR/network/ApiConfig.kt" <<'EOF'
package com.ymcchat.app.network

object ApiConfig {
    // Remplacer par ton domaine HTTPS lorsque celui-ci sera configuré.
    const val BASE_URL = "https://YOUR-DOMAIN/"
}
EOF

echo "[3/6] Création des modèles..."

cat > "$JAVA_DIR/data/auth/RegisterRequest.kt" <<'EOF'
package com.ymcchat.app.data.auth

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)
EOF

cat > "$JAVA_DIR/data/auth/AuthResponse.kt" <<'EOF'
package com.ymcchat.app.data.auth

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    val message: String?,
    val token: String,
    val user: AuthUser
)

data class AuthUser(
    val id: String,
    val username: String,
    val email: String,
    @SerializedName("created_at")
    val createdAt: String?
)
EOF

echo "[4/6] Création du service API..."

cat > "$JAVA_DIR/network/ApiService.kt" <<'EOF'
package com.ymcchat.app.network

import com.ymcchat.app.data.auth.AuthResponse
import com.ymcchat.app.data.auth.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): AuthResponse
}
EOF

cat > "$JAVA_DIR/network/ApiClient.kt" <<'EOF'
package com.ymcchat.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
EOF

echo "[5/6] Création du repository..."

cat > "$JAVA_DIR/repository/AuthRepository.kt" <<'EOF'
package com.ymcchat.app.repository

import com.ymcchat.app.data.auth.AuthResponse
import com.ymcchat.app.data.auth.RegisterRequest
import com.ymcchat.app.network.ApiClient
import java.io.IOException

class AuthRepository {

    suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<AuthResponse> {

        return try {
            val response = ApiClient.service.register(
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password
                )
            )

            Result.success(response)

        } catch (error: retrofit2.HttpException) {
            val message = when (error.code()) {
                409 -> "Ce nom d'utilisateur ou cet email est déjà utilisé."
                400 -> "Les informations envoyées sont invalides."
                else -> "Erreur serveur : ${error.code()}"
            }

            Result.failure(Exception(message))

        } catch (error: IOException) {
            Result.failure(
                Exception("Impossible de contacter le serveur.")
            )

        } catch (error: Exception) {
            Result.failure(
                Exception(error.message ?: "Une erreur est survenue.")
            )
        }
    }
}
EOF

echo "[6/6] Configuration Android..."

python3 - <<'PY'
from pathlib import Path

gradle = Path("android/app/build.gradle.kts")
content = gradle.read_text()

dependencies = [
    'implementation("com.squareup.retrofit2:retrofit:2.11.0")',
    'implementation("com.squareup.retrofit2:converter-gson:2.11.0")',
    'implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")'
]

start = content.find("dependencies")
if start == -1:
    raise SystemExit("Bloc dependencies introuvable dans build.gradle.kts")

opening = content.find("{", start)
if opening == -1:
    raise SystemExit("Bloc dependencies invalide")

depth = 0
closing = -1

for i in range(opening, len(content)):
    if content[i] == "{":
        depth += 1
    elif content[i] == "}":
        depth -= 1
        if depth == 0:
            closing = i
            break

if closing == -1:
    raise SystemExit("Impossible de trouver la fin du bloc dependencies")

existing = content[opening:closing]

missing = [
    dep for dep in dependencies
    if dep.split('"')[1] not in existing
]

if missing:
    addition = "\n    " + "\n    ".join(missing) + "\n"
    content = content[:closing] + addition + content[closing:]
    gradle.write_text(content)

manifest = Path("android/app/src/main/AndroidManifest.xml")
xml = manifest.read_text()

permission = '<uses-permission android:name="android.permission.INTERNET" />'

if "android.permission.INTERNET" not in xml:
    position = xml.find("<application")
    if position == -1:
        raise SystemExit("Balise application introuvable dans le Manifest")

    xml = xml[:position] + "    " + permission + "\n    " + xml[position:]
    manifest.write_text(xml)

print("Gradle et AndroidManifest configurés.")
PY

echo ""
echo "===================================="
echo "       INSTALLATION TERMINEE"
echo "===================================="
echo ""
echo "Fichiers Android créés :"
echo "- network/ApiConfig.kt"
echo "- network/ApiService.kt"
echo "- network/ApiClient.kt"
echo "- data/auth/RegisterRequest.kt"
echo "- data/auth/AuthResponse.kt"
echo "- repository/AuthRepository.kt"
echo ""
echo "IMPORTANT : configure ApiConfig.kt avec ton domaine HTTPS."
echo "Ensuite, compile l'application Android."
