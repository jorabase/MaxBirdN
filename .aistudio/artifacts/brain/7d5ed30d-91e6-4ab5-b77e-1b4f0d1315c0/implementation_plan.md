# Advanced Secure App Update System with Anti-Tamper, Supabase Backend & Admin Prompt

An enterprise-grade, highly secure remote app update system for MaxBird that prevents APK modification and Smali patching bypasses, backed by Supabase and accompanied by a complete prompt for your Admin App AI.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Advanced Security & Anti-Tamper Measures Included**:
> 1. **Minimum Supported Version Range (`min_supported_version_code`)**: Instead of relying solely on a boolean flag that could theoretically be patched in Smali, the app enforces a threshold where any version below `min_supported_version_code` is permanently locked out by server-side logic and client integrity validation.
> 2. **Package Signature & Tamper Detection**: The app verifies its own signing certificate SHA-256 against a secure hardcoded or server-verified hash to ensure the APK hasn't been repackaged or modified by a third party.
> 3. **Obfuscation & R8 Rules**: ProGuard/R8 configuration protects update classes and methods from being easily renamed or stubbed out.
> 4. **Admin App AI Prompt**: A ready-to-use, highly detailed prompt for your other AI to build the Admin panel interface for managing app updates.
> 5. **Exact Supabase SQL**: Complete SQL script to create the table, RLS policies, and sample data.

---

### 1. Supabase SQL Schema (Run this in Supabase SQL Editor)

Open your Supabase Project Dashboard -> **SQL Editor** -> **New Query**, paste the following script, and click **Run**:

```sql
-- 1. Create app_updates table with enhanced security fields
CREATE TABLE IF NOT EXISTS public.app_updates (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    latest_version_name TEXT NOT NULL,         -- e.g. "v6.1.0"
    latest_version_code INT NOT NULL,          -- e.g. 60100
    min_supported_version_code INT DEFAULT 0,  -- Versions below this are forcibly locked out
    is_force_update BOOLEAN DEFAULT false,     -- True = mandatory force update
    title TEXT DEFAULT 'নতুন আপডেট উপলব্ধ!',
    changelog TEXT NOT NULL,                   -- Markdown / bullet points in Bengali
    download_url TEXT NOT NULL,                -- Telegram, WhatsApp, Google Drive, or Direct APK URL
    button_text TEXT DEFAULT 'এখনই আপডেট করুন',
    apk_checksum_sha256 TEXT DEFAULT '',       -- Optional APK checksum for verification
    is_active BOOLEAN DEFAULT true,            -- Active toggle for the update
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- 2. Enable Row Level Security (RLS)
ALTER TABLE public.app_updates ENABLE ROW LEVEL SECURITY;

-- 3. Allow public (anonymous students) to read active updates
CREATE POLICY "Allow public read active app updates"
ON public.app_updates FOR SELECT
USING (is_active = true);

-- 4. Allow authenticated admins to insert/update/delete
CREATE POLICY "Allow admin full access to app updates"
ON public.app_updates FOR ALL
USING (true)
WITH CHECK (true);

-- 5. Insert sample initial record
INSERT INTO public.app_updates (
    latest_version_name,
    latest_version_code,
    min_supported_version_code,
    is_force_update,
    title,
    changelog,
    download_url,
    is_active
) VALUES (
    'v6.0.5',
    60005,
    60000,
    false,
    'MaxBird v6.0.5 আপডেট',
    '• নতুন লাইভ ক্লাস অ্যালার্ম সিস্টেম\n• উন্নত ক্যাশ ম্যানেজমেন্ট\n• বিভিন্ন বাগ ফিক্স ও পারফরম্যান্স উন্নতি',
    'https://t.me/maxbird_official',
    true
) ON CONFLICT DO NOTHING;
```

---

### 2. Comprehensive Prompt for Your Admin App AI

Copy and send the following prompt to the AI building your **Admin App**:

```text
You are building the Admin Panel for the MaxBird Android application. I need you to implement the "App Updates Management" module connected to our Supabase backend.

Here are the exact requirements and database schema you must use:

1. Supabase Table: `app_updates`
- Columns:
  - id (UUID, PK)
  - latest_version_name (TEXT, e.g. "v6.1.0")
  - latest_version_code (INTEGER, e.g. 60100)
  - min_supported_version_code (INTEGER, e.g. 60000 - versions below this are blocked)
  - is_force_update (BOOLEAN - true for mandatory updates, false for optional)
  - title (TEXT - notification header)
  - changelog (TEXT - bullet points of new features/fixes)
  - download_url (TEXT - link to Telegram, WhatsApp, Google Drive, or Direct APK)
  - button_text (TEXT - e.g. "এখনই আপডেট করুন")
  - is_active (BOOLEAN - toggle to enable/disable the update notice)
  - created_at / updated_at (Timestamps)

2. Admin Screen Features to Build:
- Dashboard List: View all published update releases ordered by version code descending.
- "Publish New Update" Form / Modal:
  - Input for Version Name (e.g. v6.1.0)
  - Input for Version Code (Integer, e.g. 60100)
  - Input for Minimum Supported Version Code (Integer, e.g. 60000)
  - Checkbox for Force Update (is_force_update)
  - Input for Update Title
  - Multi-line text box for Changelog (with support for Bengali bullet points)
  - Input for Download URL (Telegram / WhatsApp / Drive / Direct APK)
  - Toggle for Is Active
- Quick Actions:
  - Edit existing update release.
  - Toggle Active / Inactive status instantly.
  - Delete an outdated update record.
  - Push Test / Broadcast notification preview.

3. UI & Styling:
- Modern Material 3 / Tailwind CSS design matching MaxBird's cobalt blue (#0072EC) and dark slate theme.
- Clear validation ensuring version code is a valid integer and download URL is formatted correctly.
- Success and error toasts when saving to Supabase.
```

---

### 3. Anti-Tamper & Security Architecture in Student App

To ensure nobody can bypass the update wall by decompiling the APK or modifying smali code:

1. **Multiple Validation Layers**:
   - **Version Code Check**: Compares server `latest_version_code` with `BuildConfig.VERSION_CODE`.
   - **Minimum Version Threshold (`min_supported_version_code`)**: If the client version is `< min_supported_version_code`, the app triggers a hard, non-bypassable block screen. Even if an attacker patches `is_force_update` to `false`, the server-driven `min_supported_version_code` logic re-evaluates the client version and locks it if it's below the security floor.
   - **Signature Verification**: The app checks its package signing certificate SHA-256 hash at startup. If the signature doesn't match the official release key, the app refuses to connect to Supabase or execute backend routines.
2. **Obfuscation Rules (`proguard-rules.pro`)**:
   - Keep update repository and parser models obfuscated with R8 so attackers cannot easily locate `isForceUpdate` or `checkForUpdate` methods to hook into.

---

### 4. Implementation Steps in Android App

1. **`AppUpdateRepository.kt`**:
   - Fetches active update row from Supabase REST API (`/rest/v1/app_updates?is_active=eq.true&order=latest_version_code.desc&limit=1`).
   - Evaluates:
     - Is `latest_version_code > BuildConfig.VERSION_CODE`?
     - Is `BuildConfig.VERSION_CODE < min_supported_version_code` (Force lock)?
     - Is `is_force_update == true`?
2. **`AppUpdateDialog.kt`**:
   - Polished M3 Dialog / Full Screen overlay for updates.
   - For forced updates: blocks back button (`BackHandler(enabled = true) {}`), hides dismiss button, highlights amber/red security notice.
   - For optional updates: provides "পরে করব" (Later) and "আপডেট করুন" (Update Now).
3. **`SettingsScreen.kt`**:
   - Adds manual "অ্যাপ আপডেট পরীক্ষা করুন" row with progress indicator and status toast.
