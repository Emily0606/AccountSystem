# Account System

帳號管理系統，提供註冊、登入、修改個人資料等功能的 REST API。

---

## 環境需求

| 工具 | 版本 | 用途 |
|------|------|------|
| Java JDK | 21+ | 本地開發 / 執行 |
| Docker Desktop | 最新版 | Docker 方式啟動 |
| MySQL | 8.0+ | 資料庫（本地開發或 Docker 方式皆需） |

---

## 方式一：Docker 啟動（推薦）

> 不需要在本機安裝 Java，Docker 會自動在容器內編譯並打包為 JAR。

### 第一步：下載專案

```bash
git clone <專案網址>
cd AccountSystem
```

### 第二步：建立設定檔

把範本複製一份，命名為 `.env`：

```bash
cp docker/.env.example .env
```

用文字編輯器打開 `.env`，填入你的設定：

```env
# MySQL 連線網址（使用 Docker 啟動時不需更改）
DB_URL=jdbc:mysql://host.docker.internal:3306/account_system?useSSL=false&serverTimezone=Asia/Taipei&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true

# 你的 MySQL 帳號（預設通常是 root）
DB_USERNAME=root

# 你的 MySQL 密碼（安裝 MySQL 時設定的那組）
DB_PASSWORD=password

# 自訂一組密鑰，隨便打但至少 32 個字元
JWT_SECRET=your-secret-key-at-least-32-chars
```

> `.env` 包含密碼，已加入 `.gitignore`，不會被上傳至 Git。

### 第三步：建置並啟動

```bash
# 建置 Docker Image（會在容器內自動編譯並打包 JAR，第一次需幾分鐘）
docker build -f docker/Dockerfile -t account-system:latest .

# 啟動容器
docker run -d --name account-system -p 8080:8080 --env-file .env account-system:latest
```

啟動後開啟瀏覽器，前往 `http://localhost:8080/swagger-ui.html` 可看到 API 文件。

### 常用 Docker 指令

```bash
# 查看應用程式 log
docker logs account-system

# 停止應用程式
docker stop account-system

# 再次啟動（已存在的容器）
docker start account-system

# 刪除容器（之後可重新 docker run）
docker rm account-system
```

---

## 方式二：本地直接啟動（需要 Java 21）

適合開發者想在本機直接跑、看即時 log 或開啟 debug 模式。

### 第一步：設定環境變數

確認本機有 MySQL 在執行，建立 `.env` 並填入連線資訊（同方式一），或直接設定環境變數：

```bash
export DB_URL=jdbc:mysql://localhost:3306/account_system?useSSL=false&serverTimezone=Asia/Taipei&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
export DB_USERNAME=root
export DB_PASSWORD=password
export JWT_SECRET=your-secret-key-at-least-32-chars
```

### 第二步：啟動

```bash
# Windows
gradlew.bat bootRun

# macOS / Linux
./gradlew bootRun
```

### 其他開發指令

```bash
# 執行所有測試（使用 H2 in-memory，不需要 MySQL）
gradlew.bat test

# 打包成 JAR（一般不需要，Docker 會自動處理）
gradlew.bat bootJar

# 清除舊的 build 產出（只在 build 結果異常時使用）
gradlew.bat clean bootRun
```

> **關於 `clean`：** Docker 建置時不需要加 `clean`，因為每次 Docker build 都是全新環境。
> 本地開發正常情況下 `gradlew bootRun` 即可，只有在出現 stale class 等奇怪編譯錯誤時才需要加 `clean`。

---

## API 文件

啟動後，瀏覽器開啟 `http://localhost:8080/swagger-ui.html` 可看到完整互動式 API 文件（Swagger UI）。

---

## API 清單

所有 API 的網址前綴為 `http://localhost:8080`。

### 不需要登入

| 方法 | 路徑 | 說明 |
|------|------|------|
| POST | `/v1/auth/register` | 註冊新帳號 |
| POST | `/v1/auth/login` | 登入，成功後會拿到 Access Token 與 Refresh Token |
| POST | `/v1/auth/refresh` | 用 Refresh Token 換新的 Token（Token Rotation） |

### 需要登入（帶 Token）

呼叫這些 API 時，需在 Header 加上登入後取得的 Access Token：

```
Authorization: Bearer 你的 AccessToken
```

| 方法 | 路徑 | 說明 |
|------|------|------|
| POST | `/v1/auth/logout` | 登出（撤銷 Refresh Token） |
| GET | `/v1/users/me` | 查看自己的個人資料 |
| PATCH | `/v1/users/me` | 修改顯示名稱或 Email |
| PATCH | `/v1/users/me/password` | 修改密碼 |
| DELETE | `/v1/users/me` | 刪除帳號（軟刪除） |

### 錯誤回應格式

所有錯誤皆回傳 JSON：

```json
{
  "status": 401,
  "message": "帳號或密碼錯誤",
  "timestamp": "2026-05-16T10:30:00"
}
```

| HTTP 狀態碼 | 說明 |
|-------------|------|
| 400 | 請求格式錯誤或欄位驗證失敗 |
| 401 | 未授權（帳密錯誤、Token 無效或過期） |
| 403 | 帳號已鎖定 |
| 409 | 資源衝突（帳號或 Email 已存在） |
| 500 | 伺服器內部錯誤 |
