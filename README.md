# Account System

帳號管理系統，提供註冊、登入、修改個人資料等功能的 REST API。

---

## 啟動前準備

你需要先安裝以下兩個工具：

- **Docker**：用來執行這個應用程式（[下載 Docker Desktop](https://www.docker.com/products/docker-desktop/)）
- **MySQL**：資料庫，需安裝在你的電腦上（[下載 MySQL](https://dev.mysql.com/downloads/installer/)）

安裝完成後，確認 MySQL 已經在執行中，再繼續下面的步驟。

---

## 啟動步驟

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

然後用文字編輯器打開 `.env`，把裡面的內容改成你自己的設定：

```env
# MySQL 連線網址（不用改，直接用這個）
DB_URL=jdbc:mysql://host.docker.internal:3306/account_system?useSSL=false&serverTimezone=Asia/Taipei&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true

# 你的 MySQL 帳號（預設通常是 root）
DB_USERNAME=root

# 你的 MySQL 密碼（安裝 MySQL 時設定的那組）
DB_PASSWORD=your_password

# 自訂一組密鑰，隨便打但至少 32 個字元
JWT_SECRET=your-secret-key-at-least-32-chars
```

> `.env` 檔案包含密碼，已設定不會被上傳到 Git。

### 第三步：建置應用程式

這個指令會把程式碼打包成 Docker Image（第一次執行需要幾分鐘）：

```bash
docker build -f docker/Dockerfile -t account-system:latest .
```

### 第四步：啟動

```bash
docker run -d --name account-system -p 8080:8080 --env-file .env account-system:latest
```

啟動後開啟瀏覽器，前往 `http://localhost:8080`，看到回應就代表成功了。

---

## 常用指令

```bash
# 查看應用程式的 log（看有沒有錯誤）
docker logs account-system

# 停止應用程式
docker stop account-system

# 再次啟動（已存在的容器）
docker start account-system

# 刪除容器（之後可重新 docker run）
docker rm account-system
```

---

## API 清單

所有 API 的網址前綴為 `http://localhost:8080`。

### 不需要登入

| 方法 | 路徑 | 說明 |
|------|------|------|
| POST | `/v1/auth/register` | 註冊新帳號 |
| POST | `/v1/auth/login` | 登入，成功後會拿到 Token |
| POST | `/v1/auth/refresh` | 用 Refresh Token 換新的 Token |

### 需要登入（帶 Token）

呼叫這些 API 時，需要在 Header 加上登入後拿到的 Token：

```
Authorization: Bearer 你的Token
```

| 方法 | 路徑 | 說明 |
|------|------|------|
| POST | `/v1/auth/logout` | 登出 |
| GET | `/v1/users/me` | 查看自己的個人資料 |
| PATCH | `/v1/users/me` | 修改名稱或 Email |
| PATCH | `/v1/users/me/password` | 修改密碼 |
| DELETE | `/v1/users/me` | 刪除帳號 |
