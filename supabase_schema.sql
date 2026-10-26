-- ========================================================
-- MAXBIRD EXCLUSIVE DEVICE ACCESS & DEVICE CONTROL SYSTEM
-- Supabase SQL Schema, Tables, Full Permissions & Atomic RPCs
-- 
-- নির্দেশিকা:
-- ১. আপনার Supabase Dashboard (https://supabase.com) এ যান।
-- ২. বাম পাশের মেনু থেকে "SQL Editor" এ ক্লিক করুন।
-- ৩. "New query" বাটনে ক্লিক করে নিচের সম্পূর্ণ কোড পেস্ট করে "Run" চাপুন।
-- ========================================================

-- ০. পূর্বের সকল ওভারলোডেড ফাংশন স্বয়ংক্রিয়ভাবে ড্রপ করা (Fixes PGRST203 Candidate Function Overload Error)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT oid::regprocedure AS func_signature 
        FROM pg_proc 
        WHERE proname IN ('activate_device', 'check_device_status', 'verify_device_heartbeat', 'admin_toggle_device_ban', 'create_access_code', 'upsert_user_gemini_api_key', 'get_user_gemini_api_key', 'get_device_course_permissions', 'admin_set_device_course_permissions')
          AND pronamespace = 'public'::regnamespace
    ) LOOP
        EXECUTE 'DROP FUNCTION IF EXISTS ' || r.func_signature || ' CASCADE';
    END LOOP;
END $$;

-- ১. এক্সটেনশন সক্রিয়করণ (UUID জেনারেশন সাপোর্ট)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ২. এক্সেস কোড টেবিল (অ্যাডমিন যে কোড তৈরি করবেন)
CREATE TABLE IF NOT EXISTS public.access_codes (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code TEXT UNIQUE NOT NULL,
    student_name TEXT DEFAULT '',
    name TEXT DEFAULT '',
    max_devices INT DEFAULT 1,
    device_limit INT DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    status TEXT DEFAULT 'active',
    duration_days INT DEFAULT 30,
    validity_days INT DEFAULT 30,
    expires_at TIMESTAMP WITH TIME ZONE,
    expiry_date TIMESTAMP WITH TIME ZONE,
    note TEXT DEFAULT '',
    batch TEXT DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- টেবিল আগে থেকে তৈরি থাকলে সম্ভাব্য সকল কলাম ও ডিফল্ট নিশ্চিত করা
ALTER TABLE public.access_codes ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS student_name TEXT DEFAULT '';
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS name TEXT DEFAULT '';
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS max_devices INT DEFAULT 1;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS device_limit INT DEFAULT 1;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'active';
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS duration_days INT DEFAULT 30;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS validity_days INT DEFAULT 30;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS expiry_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS note TEXT DEFAULT '';
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS batch TEXT DEFAULT '';
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS can_view_profiles BOOLEAN DEFAULT false;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT now();
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT now();

-- ৩. নিবন্ধিত ডিভাইস ও সেশন টেবিল
CREATE TABLE IF NOT EXISTS public.activated_devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code_id UUID REFERENCES public.access_codes(id) ON DELETE CASCADE,
    device_hash TEXT NOT NULL,
    app_signature TEXT,
    session_token TEXT NOT NULL,
    is_blocked BOOLEAN DEFAULT false,
    can_view_profiles BOOLEAN DEFAULT false,
    activated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE(code_id, device_hash)
);

ALTER TABLE public.activated_devices ADD COLUMN IF NOT EXISTS can_view_profiles BOOLEAN DEFAULT false;

-- ৪. সেন্ট্রাল ডিভাইস কন্ট্রোল টেবিল (ADMIN DEVICE MANAGEMENT TABLE)
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_hash TEXT UNIQUE NOT NULL,
    display_device_id TEXT NOT NULL,
    device_model TEXT,
    device_brand TEXT,
    device_manufacturer TEXT,
    android_version TEXT,
    sdk_version INT,
    app_version TEXT,
    applied_code TEXT,
    is_banned BOOLEAN DEFAULT false,
    can_view_profiles BOOLEAN DEFAULT false,
    ban_reason TEXT DEFAULT 'অ্যাডমিন কর্তৃক ব্যান করা হয়েছে',
    banned_at TIMESTAMP WITH TIME ZONE,
    status TEXT DEFAULT 'active', -- 'active', 'banned', 'pending'
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS can_view_profiles BOOLEAN DEFAULT false;

-- ৫. পারমিশন ও গ্রান্টস (Admin Panel ও Student App যেন অবাধে ডাটা রিড/রাইট করতে পারে)
GRANT ALL ON TABLE public.access_codes TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.activated_devices TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.devices TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;

-- সিকিউরিটি: Row Level Security (RLS) সক্রিয়করণ এবং ফুল অ্যাক্সেস পলিসি নিশ্চিতকরণ
ALTER TABLE public.access_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activated_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

-- access_codes এর জন্য RLS পলিসি (অ্যাডমিন প্যানেল থেকে কোড তৈরি, দেখা, এডিট বা ডিলিট করার জন্য)
DROP POLICY IF EXISTS "Allow admin access to access_codes" ON public.access_codes;
DROP POLICY IF EXISTS "Allow all for access_codes" ON public.access_codes;
DROP POLICY IF EXISTS "Allow public read access_codes" ON public.access_codes;
CREATE POLICY "Allow all for access_codes" ON public.access_codes
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

-- activated_devices এর জন্য RLS পলিসি
DROP POLICY IF EXISTS "Allow all for activated_devices" ON public.activated_devices;
CREATE POLICY "Allow all for activated_devices" ON public.activated_devices
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

-- devices টেবিলের জন্য RLS পলিসি (পাবলিক রিড ও অ্যাডমিন ফুল এক্সেস)
DROP POLICY IF EXISTS "Allow public read devices" ON public.devices;
DROP POLICY IF EXISTS "Allow full admin operations on devices" ON public.devices;
CREATE POLICY "Allow public read devices" ON public.devices
FOR SELECT TO anon, authenticated
USING (true);

CREATE POLICY "Allow full admin operations on devices" ON public.devices
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

-- ৬. ডিভাইস স্ট্যাটাস ও তথ্য পরীক্ষা RPC (Atomic Status Check - 6 parameters)
CREATE OR REPLACE FUNCTION public.check_device_status(
    p_device_hash TEXT,
    p_display_device_id TEXT,
    p_device_model TEXT,
    p_device_brand TEXT,
    p_android_version TEXT,
    p_app_version TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_device RECORD;
BEGIN
    INSERT INTO public.devices (
        device_hash,
        display_device_id,
        device_model,
        device_brand,
        android_version,
        app_version,
        last_seen,
        updated_at
    ) VALUES (
        p_device_hash,
        COALESCE(NULLIF(p_display_device_id, ''), 'UNKNOWN'),
        p_device_model,
        p_device_brand,
        p_android_version,
        p_app_version,
        now(),
        now()
    )
    ON CONFLICT (device_hash) DO UPDATE SET
        display_device_id = CASE WHEN EXCLUDED.display_device_id <> '' AND EXCLUDED.display_device_id <> 'UNKNOWN' THEN EXCLUDED.display_device_id ELSE public.devices.display_device_id END,
        device_model = COALESCE(NULLIF(EXCLUDED.device_model, ''), public.devices.device_model),
        device_brand = COALESCE(NULLIF(EXCLUDED.device_brand, ''), public.devices.device_brand),
        android_version = COALESCE(NULLIF(EXCLUDED.android_version, ''), public.devices.android_version),
        app_version = COALESCE(NULLIF(EXCLUDED.app_version, ''), public.devices.app_version),
        last_seen = now(),
        updated_at = now();

    SELECT is_banned, ban_reason INTO v_device FROM public.devices WHERE device_hash = p_device_hash;
    IF FOUND AND v_device.is_banned THEN
        RETURN json_build_object(
            'is_banned', true,
            'reason', COALESCE(v_device.ban_reason, 'এই ডিভাইসটি অ্যাডমিন কর্তৃক ব্যান করা হয়েছে।')
        );
    END IF;

    RETURN json_build_object('is_banned', false);
END;
$$;

-- ৬.১ সিঙ্গেল প্যারামিটার ওভারলোড (backward compatibility wrapper)
CREATE OR REPLACE FUNCTION public.check_device_status(
    p_device_hash TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN public.check_device_status(p_device_hash, 'UNKNOWN', '', '', '', '');
END;
$$;

-- ৭. ডিভাইস অ্যাক্টিভেশন স্টোরড ফাংশন (Atomic Activation & Telemetry RPC - 8 parameters)
CREATE OR REPLACE FUNCTION public.activate_device(
    p_code TEXT,
    p_device_hash TEXT,
    p_app_signature TEXT,
    p_display_device_id TEXT,
    p_device_model TEXT,
    p_device_brand TEXT,
    p_android_version TEXT,
    p_app_version TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_code_record RECORD;
    v_bound_count INT;
    v_existing_device RECORD;
    v_device_record RECORD;
    v_new_token TEXT := gen_random_uuid()::TEXT;
    v_max_devices INT := 1;
BEGIN
    -- ডিভাইসের তথ্য public.devices এ সেভ করা
    INSERT INTO public.devices (
        device_hash,
        display_device_id,
        device_model,
        device_brand,
        android_version,
        app_version,
        applied_code,
        last_seen,
        updated_at
    ) VALUES (
        p_device_hash,
        COALESCE(NULLIF(p_display_device_id, ''), 'UNKNOWN'),
        p_device_model,
        p_device_brand,
        p_android_version,
        p_app_version,
        UPPER(TRIM(p_code)),
        now(),
        now()
    )
    ON CONFLICT (device_hash) DO UPDATE SET
        display_device_id = CASE WHEN EXCLUDED.display_device_id <> '' AND EXCLUDED.display_device_id <> 'UNKNOWN' THEN EXCLUDED.display_device_id ELSE public.devices.display_device_id END,
        device_model = COALESCE(NULLIF(EXCLUDED.device_model, ''), public.devices.device_model),
        device_brand = COALESCE(NULLIF(EXCLUDED.device_brand, ''), public.devices.device_brand),
        android_version = COALESCE(NULLIF(EXCLUDED.android_version, ''), public.devices.android_version),
        app_version = COALESCE(NULLIF(EXCLUDED.app_version, ''), public.devices.app_version),
        applied_code = UPPER(TRIM(p_code)),
        last_seen = now(),
        updated_at = now();

    -- ডিভাইস ব্যান কিনা পরীক্ষা
    SELECT * INTO v_device_record FROM public.devices WHERE device_hash = p_device_hash;
    IF FOUND AND v_device_record.is_banned THEN
        RETURN json_build_object(
            'success', false,
            'banned', true,
            'is_banned', true,
            'message', COALESCE(v_device_record.ban_reason, 'এই ডিভাইসটি অ্যাডমিন দ্বারা স্থায়ীভাবে ব্যান করা হয়েছে!')
        );
    END IF;

    -- কোড পরীক্ষা
    SELECT * INTO v_code_record FROM public.access_codes WHERE UPPER(code) = UPPER(TRIM(p_code));
    IF NOT FOUND THEN
        RETURN json_build_object('success', false, 'message', 'ভুল এক্সেস কোড। সঠিক কোড দিন।');
    END IF;

    IF v_code_record.is_active IS FALSE OR (v_code_record.status IS NOT NULL AND v_code_record.status = 'inactive') THEN
        RETURN json_build_object('success', false, 'message', 'এই এক্সেস কোডটি অ্যাডমিন দ্বারা নিষ্ক্রিয় করা হয়েছে।');
    END IF;

    IF v_code_record.expires_at IS NOT NULL AND v_code_record.expires_at < now() THEN
        RETURN json_build_object('success', false, 'message', 'এক্সেস কোডের মেয়াদ শেষ হয়ে গেছে।');
    END IF;

    -- ডিভাইসটি কি আগে থেকেই এই কোডে নিবন্ধিত?
    SELECT * INTO v_existing_device FROM public.activated_devices
    WHERE code_id = v_code_record.id AND device_hash = p_device_hash;

    IF FOUND THEN
        IF v_existing_device.is_blocked THEN
            RETURN json_build_object('success', false, 'banned', true, 'is_banned', true, 'message', 'এই ডিভাইসটি ব্লক করা হয়েছে। অ্যাডমিনের সাথে যোগাযোগ করুন।');
        END IF;
        UPDATE public.activated_devices 
        SET session_token = v_new_token, last_seen = now(), app_signature = p_app_signature
        WHERE id = v_existing_device.id;

        UPDATE public.devices
        SET status = 'active', updated_at = now()
        WHERE device_hash = p_device_hash;

        RETURN json_build_object('success', true, 'token', v_new_token, 'message', 'ডিভাইস সফলভাবে পুনরায় যাচাই করা হয়েছে।');
    END IF;

    -- ডিভাইস কোটা সীমা পরীক্ষা
    v_max_devices := COALESCE(v_code_record.max_devices, v_code_record.device_limit, 1);
    SELECT count(*) INTO v_bound_count FROM public.activated_devices WHERE code_id = v_code_record.id;
    IF v_bound_count >= v_max_devices THEN
        RETURN json_build_object('success', false, 'message', 'এই কোডটি ইতিমধ্যে অন্য ফোনে ব্যবহার করা হয়েছে। ডিভাইস লিমিট শেষ।');
    END IF;

    -- নতুন ডিভাইস বাইন্ড ও টোকেন প্রদান
    INSERT INTO public.activated_devices (code_id, device_hash, app_signature, session_token)
    VALUES (v_code_record.id, p_device_hash, p_app_signature, v_new_token);

    UPDATE public.devices
    SET status = 'active', updated_at = now()
    WHERE device_hash = p_device_hash;

    RETURN json_build_object('success', true, 'token', v_new_token, 'message', 'ডিভাইস এক্সেস সফলভাবে সক্রিয় হয়েছে!');
END;
$$;

-- ৭.১ ৩-প্যারামিটার ওভারলোড (backward compatibility wrapper)
CREATE OR REPLACE FUNCTION public.activate_device(
    p_code TEXT,
    p_device_hash TEXT,
    p_app_signature TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN public.activate_device(
        p_code,
        p_device_hash,
        p_app_signature,
        'UNKNOWN',
        'Android Device',
        '',
        '',
        ''
    );
END;
$$;

-- ৮. হার্টবিট ভেরিফিকেশন স্টোরড ফাংশন (Fast Heartbeat & Ban Check RPC)
CREATE OR REPLACE FUNCTION public.verify_device_heartbeat(
    p_device_hash TEXT,
    p_token TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_device RECORD;
    v_code RECORD;
    v_is_banned BOOLEAN := false;
BEGIN
    SELECT is_banned INTO v_is_banned FROM public.devices WHERE device_hash = p_device_hash;
    IF v_is_banned THEN
        RETURN json_build_object('valid', false, 'reason', 'DEVICE_BANNED');
    END IF;

    SELECT * INTO v_device FROM public.activated_devices 
    WHERE device_hash = p_device_hash AND session_token = p_token;

    IF NOT FOUND THEN
        RETURN json_build_object('valid', false, 'reason', 'SESSION_NOT_FOUND');
    END IF;

    IF v_device.is_blocked THEN
        RETURN json_build_object('valid', false, 'reason', 'DEVICE_BLOCKED');
    END IF;

    SELECT * INTO v_code FROM public.access_codes WHERE id = v_device.code_id;
    IF NOT FOUND OR v_code.is_active IS FALSE THEN
        RETURN json_build_object('valid', false, 'reason', 'CODE_REVOKED');
    END IF;

    UPDATE public.activated_devices SET last_seen = now() WHERE id = v_device.id;
    UPDATE public.devices SET last_seen = now() WHERE device_hash = p_device_hash;
    RETURN json_build_object('valid', true);
END;
$$;

-- ৯. অ্যাডমিন ডিভাইস ব্যান ও আনব্যান করার সুবিধাজনক RPC
CREATE OR REPLACE FUNCTION public.admin_toggle_device_ban(
    p_device_hash TEXT,
    p_is_banned BOOLEAN,
    p_reason TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    UPDATE public.devices
    SET is_banned = p_is_banned,
        ban_reason = p_reason,
        banned_at = CASE WHEN p_is_banned THEN now() ELSE NULL END,
        status = CASE WHEN p_is_banned THEN 'banned' ELSE 'active' END,
        updated_at = now()
    WHERE device_hash = p_device_hash;

    UPDATE public.activated_devices
    SET is_blocked = p_is_banned
    WHERE device_hash = p_device_hash;

    RETURN json_build_object('success', true, 'is_banned', p_is_banned);
END;
$$;

-- ৯.১ অ্যাডমিন ডিভাইস ব্যান ২-প্যারামিটার ওভারলোড
CREATE OR REPLACE FUNCTION public.admin_toggle_device_ban(
    p_device_hash TEXT,
    p_is_banned BOOLEAN
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN public.admin_toggle_device_ban(p_device_hash, p_is_banned, 'অ্যাডমিন কর্তৃক ব্যান করা হয়েছে');
END;
$$;

-- ১০. অ্যাডমিন প্যানেল সিঙ্ক ট্রিগার: devices টেবিলে ব্যান টগল করলে সাথে সাথে activated_devices ও সিঙ্ক হবে
CREATE OR REPLACE FUNCTION public.sync_device_ban_status()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    UPDATE public.activated_devices
    SET is_blocked = NEW.is_banned
    WHERE device_hash = NEW.device_hash;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_device_ban ON public.devices;
CREATE TRIGGER trg_sync_device_ban
AFTER UPDATE OF is_banned ON public.devices
FOR EACH ROW EXECUTE FUNCTION public.sync_device_ban_status();

-- ১১. অ্যাডমিন কোড তৈরির সুবিধাজনক RPC (Admin Create Access Code RPC)
CREATE OR REPLACE FUNCTION public.create_access_code(
    p_code TEXT,
    p_student_name TEXT,
    p_max_devices INT,
    p_duration_days INT,
    p_note TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_expires_at TIMESTAMP WITH TIME ZONE;
    v_new_record RECORD;
BEGIN
    IF p_duration_days > 0 THEN
        v_expires_at := now() + (p_duration_days || ' days')::INTERVAL;
    ELSE
        v_expires_at := NULL;
    END IF;

    INSERT INTO public.access_codes (
        code, student_name, name, max_devices, device_limit,
        duration_days, expires_at, note, is_active, status, created_at, updated_at
    ) VALUES (
        UPPER(TRIM(p_code)), p_student_name, p_student_name,
        COALESCE(p_max_devices, 1), COALESCE(p_max_devices, 1),
        p_duration_days, v_expires_at, p_note, true, 'active', now(), now()
    )
    RETURNING * INTO v_new_record;

    RETURN json_build_object(
        'success', true,
        'message', 'এক্সেস কোড সফলভাবে তৈরি হয়েছে।',
        'code', v_new_record.code,
        'id', v_new_record.id,
        'expires_at', v_new_record.expires_at
    );
EXCEPTION WHEN OTHERS THEN
    RETURN json_build_object(
        'success', false,
        'message', SQLERRM
    );
END;
$$;

-- ১২. ডায়নামিক সাপোর্ট লিংকস টেবিল
CREATE TABLE IF NOT EXISTS public.activation_support_links (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    title TEXT NOT NULL,
    subtitle TEXT,
    icon_type TEXT DEFAULT 'telegram',
    url TEXT NOT NULL,
    color_hex TEXT DEFAULT '#38BDF8',
    priority INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

ALTER TABLE public.activation_support_links ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow public read support links" ON public.activation_support_links;
CREATE POLICY "Allow public read support links" ON public.activation_support_links
FOR SELECT TO anon, authenticated
USING (is_active = true);

DROP POLICY IF EXISTS "Allow full admin support links" ON public.activation_support_links;
CREATE POLICY "Allow full admin support links" ON public.activation_support_links
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

GRANT ALL ON TABLE public.activation_support_links TO anon, authenticated, service_role;

-- ১৩. BIRDGRAM টেলিগ্রাম ক্লাউড সেশন বাইন্ডিং টেবিল (Auto-restore on logout / reinstall)
CREATE TABLE IF NOT EXISTS public.user_telegram_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    access_key TEXT UNIQUE NOT NULL,      -- student access code / user ID
    student_name TEXT DEFAULT '',
    user_phone TEXT DEFAULT '',
    cookies_data TEXT DEFAULT '',
    local_storage_data TEXT DEFAULT '',   -- JSON stringified localStorage
    session_variant TEXT DEFAULT 'WEB_K',
    is_active BOOLEAN DEFAULT true,
    last_synced_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

ALTER TABLE public.user_telegram_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow all access user_telegram_sessions" ON public.user_telegram_sessions;
CREATE POLICY "Allow all access user_telegram_sessions" ON public.user_telegram_sessions
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

GRANT ALL ON TABLE public.user_telegram_sessions TO anon, authenticated, service_role;

-- ========================================================
-- ১৪. GEMINI AI STUDY API KEY CLOUD SYNC TABLE & RPCs
-- (অ্যাপ ডিলিট বা অন্য ফোনে লগইন করলেও অটোমেটিক API Key চলে আসবে)
-- ========================================================
CREATE TABLE IF NOT EXISTS public.user_gemini_api_keys (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    account_key TEXT UNIQUE NOT NULL,     -- প্রাইমারি একাউন্ট কী (user_id / phone / access_code)
    user_id TEXT DEFAULT '',              -- Shikho একাউন্টের user_id
    user_phone TEXT DEFAULT '',           -- শিক্ষার্থীর মোবাইল নম্বর (যেমন: 88017...)
    access_key TEXT DEFAULT '',           -- অ্যাক্টিভেশন এক্সেস কোড
    student_name TEXT DEFAULT '',         -- শিক্ষার্থীর নাম
    gemini_api_key TEXT NOT NULL DEFAULT '',
    preferred_model TEXT DEFAULT 'gemini-3.8-flash',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

ALTER TABLE public.user_gemini_api_keys ADD COLUMN IF NOT EXISTS user_id TEXT DEFAULT '';
ALTER TABLE public.user_gemini_api_keys ADD COLUMN IF NOT EXISTS user_phone TEXT DEFAULT '';
ALTER TABLE public.user_gemini_api_keys ADD COLUMN IF NOT EXISTS access_key TEXT DEFAULT '';
ALTER TABLE public.user_gemini_api_keys ADD COLUMN IF NOT EXISTS student_name TEXT DEFAULT '';
ALTER TABLE public.user_gemini_api_keys ADD COLUMN IF NOT EXISTS gemini_api_key TEXT NOT NULL DEFAULT '';
ALTER TABLE public.user_gemini_api_keys ADD COLUMN IF NOT EXISTS preferred_model TEXT DEFAULT 'gemini-3.8-flash';
ALTER TABLE public.user_gemini_api_keys ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;

CREATE INDEX IF NOT EXISTS idx_gemini_keys_user_id ON public.user_gemini_api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_gemini_keys_user_phone ON public.user_gemini_api_keys(user_phone);
CREATE INDEX IF NOT EXISTS idx_gemini_keys_access_key ON public.user_gemini_api_keys(access_key);

ALTER TABLE public.user_gemini_api_keys ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow all access user_gemini_api_keys" ON public.user_gemini_api_keys;
CREATE POLICY "Allow all access user_gemini_api_keys" ON public.user_gemini_api_keys
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

GRANT ALL ON TABLE public.user_gemini_api_keys TO anon, authenticated, service_role;

-- ১৪.১ ইউজারের Gemini API Key ডাটাবেজে সেভ বা আপডেট করার RPC
CREATE OR REPLACE FUNCTION public.upsert_user_gemini_api_key(
    p_account_key TEXT,
    p_user_id TEXT,
    p_user_phone TEXT,
    p_access_key TEXT,
    p_student_name TEXT,
    p_gemini_api_key TEXT,
    p_preferred_model TEXT DEFAULT 'gemini-3.8-flash'
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_existing_id UUID;
    v_clean_account_key TEXT := COALESCE(NULLIF(TRIM(p_account_key), ''), NULLIF(TRIM(p_user_id), ''), NULLIF(TRIM(p_user_phone), ''), NULLIF(TRIM(p_access_key), ''), 'default_user');
BEGIN
    SELECT id INTO v_existing_id
    FROM public.user_gemini_api_keys
    WHERE account_key = v_clean_account_key
       OR (NULLIF(TRIM(p_user_id), '') IS NOT NULL AND user_id = TRIM(p_user_id))
       OR (NULLIF(TRIM(p_user_phone), '') IS NOT NULL AND user_phone = TRIM(p_user_phone))
    ORDER BY updated_at DESC
    LIMIT 1;

    IF v_existing_id IS NOT NULL THEN
        UPDATE public.user_gemini_api_keys
        SET account_key = v_clean_account_key,
            user_id = COALESCE(NULLIF(TRIM(p_user_id), ''), user_id),
            user_phone = COALESCE(NULLIF(TRIM(p_user_phone), ''), user_phone),
            access_key = COALESCE(NULLIF(TRIM(p_access_key), ''), access_key),
            student_name = COALESCE(NULLIF(TRIM(p_student_name), ''), student_name),
            gemini_api_key = TRIM(p_gemini_api_key),
            preferred_model = COALESCE(NULLIF(TRIM(p_preferred_model), ''), 'gemini-3.8-flash'),
            is_active = true,
            updated_at = now()
        WHERE id = v_existing_id;
    ELSE
        INSERT INTO public.user_gemini_api_keys (
            account_key, user_id, user_phone, access_key, student_name, gemini_api_key, preferred_model, is_active, created_at, updated_at
        ) VALUES (
            v_clean_account_key,
            COALESCE(TRIM(p_user_id), ''),
            COALESCE(TRIM(p_user_phone), ''),
            COALESCE(TRIM(p_access_key), ''),
            COALESCE(TRIM(p_student_name), ''),
            TRIM(p_gemini_api_key),
            COALESCE(NULLIF(TRIM(p_preferred_model), ''), 'gemini-3.8-flash'),
            true,
            now(),
            now()
        )
        ON CONFLICT (account_key) DO UPDATE SET
            user_id = COALESCE(NULLIF(EXCLUDED.user_id, ''), public.user_gemini_api_keys.user_id),
            user_phone = COALESCE(NULLIF(EXCLUDED.user_phone, ''), public.user_gemini_api_keys.user_phone),
            access_key = COALESCE(NULLIF(EXCLUDED.access_key, ''), public.user_gemini_api_keys.access_key),
            student_name = COALESCE(NULLIF(EXCLUDED.student_name, ''), public.user_gemini_api_keys.student_name),
            gemini_api_key = EXCLUDED.gemini_api_key,
            preferred_model = EXCLUDED.preferred_model,
            updated_at = now();
    END IF;

    RETURN json_build_object(
        'success', true,
        'account_key', v_clean_account_key,
        'message', 'Gemini API Key ক্লাউড ডাটাবেজে সফলভাবে সংরক্ষিত হয়েছে।'
    );
END;
$$;

-- ১৪.২ লগইন করার সাথে সাথে ইউজারের Gemini API Key ডাটাবেজ থেকে নিয়ে আসার RPC
CREATE OR REPLACE FUNCTION public.get_user_gemini_api_key(
    p_account_key TEXT,
    p_user_id TEXT DEFAULT '',
    p_user_phone TEXT DEFAULT '',
    p_access_key TEXT DEFAULT ''
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_record RECORD;
BEGIN
    SELECT * INTO v_record
    FROM public.user_gemini_api_keys
    WHERE is_active = true
      AND gemini_api_key <> ''
      AND (
          (NULLIF(TRIM(p_account_key), '') IS NOT NULL AND account_key = TRIM(p_account_key))
          OR (NULLIF(TRIM(p_user_id), '') IS NOT NULL AND user_id = TRIM(p_user_id))
          OR (NULLIF(TRIM(p_user_phone), '') IS NOT NULL AND user_phone = TRIM(p_user_phone))
          OR (NULLIF(TRIM(p_access_key), '') IS NOT NULL AND access_key = TRIM(p_access_key))
      )
    ORDER BY updated_at DESC
    LIMIT 1;

    IF FOUND THEN
        RETURN json_build_object(
            'found', true,
            'gemini_api_key', v_record.gemini_api_key,
            'preferred_model', COALESCE(v_record.preferred_model, 'gemini-3.8-flash'),
            'student_name', COALESCE(v_record.student_name, ''),
            'updated_at', v_record.updated_at
        );
    END IF;

    RETURN json_build_object('found', false);
END;
$$;

-- ========================================================
-- ১৫. DEVICE & SHIKHO ACCOUNT BOUND GRANULAR SYLLABUS & COURSE ACCESS CONTROL
-- (অ্যাডমিন অ্যাপ থেকে নির্দিষ্ট ডিভাইসের আন্ডারে লগইন করা নির্দিষ্ট শিখো অ্যাকাউন্ট সিলেক্ট করে শ্রেণি, বিভাগ, ব্যাচ এবং কোর্স লক/এক্সেস দেওয়ার সিস্টেম)
-- ========================================================

-- ১৫.১ বিদ্যমান টেবিলগুলোতে নতুন পারমিশন ও বাউন্ড অ্যাকাউন্ট কলাম যুক্ত করা
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS is_access_restricted BOOLEAN DEFAULT false;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS bound_shikho_user_id TEXT DEFAULT '';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS bound_user_phone TEXT DEFAULT '';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_classes TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_groups TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_batches TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_programs TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS shikho_access_token TEXT DEFAULT '';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS shikho_refresh_token TEXT DEFAULT '';

ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS is_access_restricted BOOLEAN DEFAULT false;
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS allowed_classes TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS allowed_groups TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS allowed_batches TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.access_codes ADD COLUMN IF NOT EXISTS allowed_programs TEXT[] DEFAULT '{}'::TEXT[];

ALTER TABLE public.activated_devices ADD COLUMN IF NOT EXISTS is_access_restricted BOOLEAN DEFAULT false;
ALTER TABLE public.activated_devices ADD COLUMN IF NOT EXISTS allowed_classes TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.activated_devices ADD COLUMN IF NOT EXISTS allowed_groups TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.activated_devices ADD COLUMN IF NOT EXISTS allowed_batches TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.activated_devices ADD COLUMN IF NOT EXISTS allowed_programs TEXT[] DEFAULT '{}'::TEXT[];

-- ১৫.২ প্রতিটি ডিভাইসের আন্ডারে যতগুলো শিখো অ্যাকাউন্ট লগইন করবে তার তালিকা ও পারমিশন টেবিল
CREATE TABLE IF NOT EXISTS public.device_shikho_accounts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_hash TEXT NOT NULL,
    display_device_id TEXT DEFAULT '',
    shikho_user_id TEXT NOT NULL,
    user_phone TEXT DEFAULT '',
    student_name TEXT DEFAULT '',
    user_avatar TEXT DEFAULT '',
    school_name TEXT DEFAULT '',
    current_class TEXT DEFAULT '',
    current_group TEXT DEFAULT '',
    current_batch TEXT DEFAULT '',
    is_access_granted BOOLEAN DEFAULT false,
    is_access_restricted BOOLEAN DEFAULT false,
    allowed_classes TEXT[] DEFAULT '{}'::TEXT[],
    allowed_groups TEXT[] DEFAULT '{}'::TEXT[],
    allowed_batches TEXT[] DEFAULT '{}'::TEXT[],
    allowed_programs TEXT[] DEFAULT '{}'::TEXT[],
    first_login_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    last_seen_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE(device_hash, shikho_user_id)
);

CREATE INDEX IF NOT EXISTS idx_device_shikho_accounts_device_hash ON public.device_shikho_accounts(device_hash);
CREATE INDEX IF NOT EXISTS idx_device_shikho_accounts_user_id ON public.device_shikho_accounts(shikho_user_id);
CREATE INDEX IF NOT EXISTS idx_device_shikho_accounts_phone ON public.device_shikho_accounts(user_phone);

ALTER TABLE public.device_shikho_accounts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow all access device_shikho_accounts" ON public.device_shikho_accounts;
CREATE POLICY "Allow all access device_shikho_accounts" ON public.device_shikho_accounts
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

GRANT ALL ON TABLE public.device_shikho_accounts TO anon, authenticated, service_role;

-- ১৫.৩ কোর্স ক্যাটালগ টেবিল (যাতে অ্যাডমিন অ্যাপে সব কোর্সের নাম ও আইডি ক্যাশ থাকে)
CREATE TABLE IF NOT EXISTS public.available_courses_catalog (
    program_id TEXT PRIMARY KEY,
    title_bn TEXT NOT NULL,
    class_code TEXT DEFAULT '',
    group_code TEXT DEFAULT 'None',
    batch_id TEXT DEFAULT '',
    banner_url TEXT DEFAULT '',
    is_free BOOLEAN DEFAULT false,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

ALTER TABLE public.available_courses_catalog ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow all access available_courses_catalog" ON public.available_courses_catalog;
CREATE POLICY "Allow all access available_courses_catalog" ON public.available_courses_catalog
FOR ALL TO anon, authenticated
USING (true)
WITH CHECK (true);

GRANT ALL ON TABLE public.available_courses_catalog TO anon, authenticated, service_role;

-- ১৫.৪ স্টুডেন্ট অ্যাপ থেকে ডিভাইস ও শিখো অ্যাকাউন্টের কোর্স পারমিশন রিড করার RPC
-- (অন্য কোনো আন-অথরাইজড শিখো অ্যাকাউন্ট দিয়ে একই ফোনে লগইন করলে অটোমেটিক এক্সেস ব্লক করে দেবে)
CREATE OR REPLACE FUNCTION public.get_device_course_permissions(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_code TEXT DEFAULT '',
    p_shikho_user_id TEXT DEFAULT '',
    p_user_phone TEXT DEFAULT ''
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_acc RECORD;
    v_other_acc RECORD;
    v_dev RECORD;
    v_code RECORD;
    v_act RECORD;
    v_clean_uid TEXT := COALESCE(TRIM(p_shikho_user_id), '');
    v_clean_phone TEXT := COALESCE(TRIM(p_user_phone), '');
BEGIN
    -- ১. যদি ইউজার কোনো শিখো অ্যাকাউন্টে লগইন থাকে, তবে প্রথমে ওই অ্যাকাউন্টের নির্দিষ্ট পারমিশন চেক করা
    IF v_clean_uid <> '' OR v_clean_phone <> '' THEN
        SELECT * INTO v_acc
        FROM public.device_shikho_accounts
        WHERE (device_hash = p_device_hash OR (NULLIF(TRIM(p_display_device_id), '') IS NOT NULL AND display_device_id = TRIM(p_display_device_id)))
          AND (
              (v_clean_uid <> '' AND shikho_user_id = v_clean_uid)
              OR (v_clean_phone <> '' AND user_phone <> '' AND user_phone = v_clean_phone)
          )
        ORDER BY is_access_granted DESC, is_access_restricted DESC, last_seen_at DESC
        LIMIT 1;

        IF FOUND AND (
            v_acc.is_access_granted = true
            OR v_acc.is_access_restricted = true
            OR COALESCE(array_length(v_acc.allowed_classes, 1), 0) > 0
            OR COALESCE(array_length(v_acc.allowed_groups, 1), 0) > 0
            OR COALESCE(array_length(v_acc.allowed_batches, 1), 0) > 0
            OR COALESCE(array_length(v_acc.allowed_programs, 1), 0) > 0
        ) THEN
            RETURN json_build_object(
                'is_access_restricted', true,
                'is_account_authorized', true,
                'bound_user_id', COALESCE(v_acc.shikho_user_id, v_clean_uid),
                'bound_user_phone', COALESCE(v_acc.user_phone, v_clean_phone),
                'allowed_classes', COALESCE(v_acc.allowed_classes, '{}'::TEXT[]),
                'allowed_groups', COALESCE(v_acc.allowed_groups, '{}'::TEXT[]),
                'allowed_batches', COALESCE(v_acc.allowed_batches, '{}'::TEXT[]),
                'allowed_programs', COALESCE(v_acc.allowed_programs, '{}'::TEXT[])
            );
        END IF;

        -- ২. চেক করা যে এই একই ডিভাইসে অন্য কোনো শিখো অ্যাকাউন্টকে অ্যাডমিন এক্সেস দিয়েছেন কি না!
        -- যদি অন্য অ্যাকাউন্টকে এক্সেস দেওয়া থাকে, তাহলে বর্তমান অ্যাকাউন্টটি সম্পূর্ণ আন-অথরাইজড (বাইপাস রোধ)!
        SELECT * INTO v_other_acc
        FROM public.device_shikho_accounts
        WHERE (device_hash = p_device_hash OR (NULLIF(TRIM(p_display_device_id), '') IS NOT NULL AND display_device_id = TRIM(p_display_device_id)))
          AND (
              is_access_granted = true
              OR is_access_restricted = true
              OR COALESCE(array_length(allowed_classes, 1), 0) > 0
              OR COALESCE(array_length(allowed_groups, 1), 0) > 0
              OR COALESCE(array_length(allowed_batches, 1), 0) > 0
              OR COALESCE(array_length(allowed_programs, 1), 0) > 0
          )
        ORDER BY is_access_granted DESC, last_seen_at DESC
        LIMIT 1;

        IF FOUND THEN
            RETURN json_build_object(
                'is_access_restricted', true,
                'is_account_authorized', false,
                'bound_user_id', COALESCE(v_other_acc.shikho_user_id, ''),
                'bound_user_phone', COALESCE(v_other_acc.user_phone, ''),
                'allowed_classes', '{}'::TEXT[],
                'allowed_groups', '{}'::TEXT[],
                'allowed_batches', '{}'::TEXT[],
                'allowed_programs', ARRAY['__UNAUTHORIZED_ACCOUNT__']::TEXT[]
            );
        END IF;
    END IF;

    -- ৩. public.devices টেবিল চেক করা
    SELECT is_access_restricted, bound_shikho_user_id, bound_user_phone, allowed_classes, allowed_groups, allowed_batches, allowed_programs
    INTO v_dev
    FROM public.devices
    WHERE device_hash = p_device_hash
       OR (NULLIF(TRIM(p_display_device_id), '') IS NOT NULL AND display_device_id = TRIM(p_display_device_id))
    ORDER BY is_access_restricted DESC, updated_at DESC
    LIMIT 1;

    IF FOUND AND (
        v_dev.is_access_restricted = true
        OR COALESCE(array_length(v_dev.allowed_classes, 1), 0) > 0
        OR COALESCE(array_length(v_dev.allowed_groups, 1), 0) > 0
        OR COALESCE(array_length(v_dev.allowed_batches, 1), 0) > 0
        OR COALESCE(array_length(v_dev.allowed_programs, 1), 0) > 0
    ) THEN
        -- যদি ডিভাইসে নির্দিষ্ট বাউন্ড অ্যাকাউন্ট সেট করা থাকে এবং বর্তমান লগইন করা অ্যাকাউন্ট ভিন্ন হয়:
        IF (NULLIF(TRIM(v_dev.bound_shikho_user_id), '') IS NOT NULL AND v_clean_uid <> '' AND TRIM(v_dev.bound_shikho_user_id) <> v_clean_uid)
           AND (NULLIF(TRIM(v_dev.bound_user_phone), '') IS NULL OR v_clean_phone = '' OR TRIM(v_dev.bound_user_phone) <> v_clean_phone) THEN
            RETURN json_build_object(
                'is_access_restricted', true,
                'is_account_authorized', false,
                'bound_user_id', COALESCE(v_dev.bound_shikho_user_id, ''),
                'bound_user_phone', COALESCE(v_dev.bound_user_phone, ''),
                'allowed_classes', '{}'::TEXT[],
                'allowed_groups', '{}'::TEXT[],
                'allowed_batches', '{}'::TEXT[],
                'allowed_programs', ARRAY['__UNAUTHORIZED_ACCOUNT__']::TEXT[]
            );
        END IF;

        RETURN json_build_object(
            'is_access_restricted', true,
            'is_account_authorized', true,
            'bound_user_id', COALESCE(NULLIF(TRIM(v_dev.bound_shikho_user_id), ''), v_clean_uid),
            'bound_user_phone', COALESCE(NULLIF(TRIM(v_dev.bound_user_phone), ''), v_clean_phone),
            'allowed_classes', COALESCE(v_dev.allowed_classes, '{}'::TEXT[]),
            'allowed_groups', COALESCE(v_dev.allowed_groups, '{}'::TEXT[]),
            'allowed_batches', COALESCE(v_dev.allowed_batches, '{}'::TEXT[]),
            'allowed_programs', COALESCE(v_dev.allowed_programs, '{}'::TEXT[])
        );
    END IF;

    -- ৪. ব্যবহৃত access_codes টেবিল চেক করা
    IF NULLIF(TRIM(p_code), '') IS NOT NULL THEN
        SELECT is_access_restricted, allowed_classes, allowed_groups, allowed_batches, allowed_programs
        INTO v_code
        FROM public.access_codes
        WHERE UPPER(code) = UPPER(TRIM(p_code))
        LIMIT 1;

        IF FOUND AND (
            v_code.is_access_restricted = true
            OR COALESCE(array_length(v_code.allowed_classes, 1), 0) > 0
            OR COALESCE(array_length(v_code.allowed_groups, 1), 0) > 0
            OR COALESCE(array_length(v_code.allowed_batches, 1), 0) > 0
            OR COALESCE(array_length(v_code.allowed_programs, 1), 0) > 0
        ) THEN
            RETURN json_build_object(
                'is_access_restricted', true,
                'is_account_authorized', true,
                'bound_user_id', v_clean_uid,
                'bound_user_phone', v_clean_phone,
                'allowed_classes', COALESCE(v_code.allowed_classes, '{}'::TEXT[]),
                'allowed_groups', COALESCE(v_code.allowed_groups, '{}'::TEXT[]),
                'allowed_batches', COALESCE(v_code.allowed_batches, '{}'::TEXT[]),
                'allowed_programs', COALESCE(v_code.allowed_programs, '{}'::TEXT[])
            );
        END IF;
    END IF;

    -- ৫. activated_devices টেবিল চেক করা
    SELECT is_access_restricted, allowed_classes, allowed_groups, allowed_batches, allowed_programs
    INTO v_act
    FROM public.activated_devices
    WHERE device_hash = p_device_hash
    ORDER BY is_access_restricted DESC, activated_at DESC
    LIMIT 1;

    IF FOUND AND (
        v_act.is_access_restricted = true
        OR COALESCE(array_length(v_act.allowed_classes, 1), 0) > 0
        OR COALESCE(array_length(v_act.allowed_groups, 1), 0) > 0
        OR COALESCE(array_length(v_act.allowed_batches, 1), 0) > 0
        OR COALESCE(array_length(v_act.allowed_programs, 1), 0) > 0
    ) THEN
        RETURN json_build_object(
            'is_access_restricted', true,
            'is_account_authorized', true,
            'bound_user_id', v_clean_uid,
            'bound_user_phone', v_clean_phone,
            'allowed_classes', COALESCE(v_act.allowed_classes, '{}'::TEXT[]),
            'allowed_groups', COALESCE(v_act.allowed_groups, '{}'::TEXT[]),
            'allowed_batches', COALESCE(v_act.allowed_batches, '{}'::TEXT[]),
            'allowed_programs', COALESCE(v_act.allowed_programs, '{}'::TEXT[])
        );
    END IF;

    RETURN json_build_object(
        'is_access_restricted', false,
        'is_account_authorized', true,
        'bound_user_id', v_clean_uid,
        'bound_user_phone', v_clean_phone,
        'allowed_classes', '{}'::TEXT[],
        'allowed_groups', '{}'::TEXT[],
        'allowed_batches', '{}'::TEXT[],
        'allowed_programs', '{}'::TEXT[]
    );
END;
$$;

-- ১৫.৫ স্টুডেন্ট অ্যাপে যতগুলো শিখো অ্যাকাউন্ট লগইন হবে তা অটোমেটিক ডিভাইসের আন্ডারে রেজিস্টার করার RPC
CREATE OR REPLACE FUNCTION public.register_device_shikho_account(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_shikho_user_id TEXT DEFAULT '',
    p_user_phone TEXT DEFAULT '',
    p_student_name TEXT DEFAULT '',
    p_user_avatar TEXT DEFAULT '',
    p_school_name TEXT DEFAULT '',
    p_current_class TEXT DEFAULT '',
    p_current_group TEXT DEFAULT '',
    p_current_batch TEXT DEFAULT ''
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_uid TEXT := COALESCE(NULLIF(TRIM(p_shikho_user_id), ''), NULLIF(TRIM(p_user_phone), ''));
    v_dev RECORD;
    v_has_any_granted BOOLEAN := false;
BEGIN
    IF v_uid IS NULL OR v_uid = '' OR COALESCE(TRIM(p_device_hash), '') = '' THEN
        RETURN public.get_device_course_permissions(p_device_hash, p_display_device_id, '', p_shikho_user_id, p_user_phone);
    END IF;

    -- ১. ডিভাইসের আন্ডারে এই শিখো অ্যাকাউন্টটি Upsert করা
    INSERT INTO public.device_shikho_accounts (
        device_hash,
        display_device_id,
        shikho_user_id,
        user_phone,
        student_name,
        user_avatar,
        school_name,
        current_class,
        current_group,
        current_batch,
        first_login_at,
        last_seen_at
    ) VALUES (
        TRIM(p_device_hash),
        COALESCE(TRIM(p_display_device_id), ''),
        v_uid,
        COALESCE(TRIM(p_user_phone), ''),
        COALESCE(TRIM(p_student_name), ''),
        COALESCE(TRIM(p_user_avatar), ''),
        COALESCE(TRIM(p_school_name), ''),
        COALESCE(TRIM(p_current_class), ''),
        COALESCE(TRIM(p_current_group), ''),
        COALESCE(TRIM(p_current_batch), ''),
        now(),
        now()
    )
    ON CONFLICT (device_hash, shikho_user_id) DO UPDATE SET
        display_device_id = CASE WHEN EXCLUDED.display_device_id <> '' THEN EXCLUDED.display_device_id ELSE public.device_shikho_accounts.display_device_id END,
        user_phone = CASE WHEN EXCLUDED.user_phone <> '' THEN EXCLUDED.user_phone ELSE public.device_shikho_accounts.user_phone END,
        student_name = CASE WHEN EXCLUDED.student_name <> '' THEN EXCLUDED.student_name ELSE public.device_shikho_accounts.student_name END,
        user_avatar = CASE WHEN EXCLUDED.user_avatar <> '' THEN EXCLUDED.user_avatar ELSE public.device_shikho_accounts.user_avatar END,
        school_name = CASE WHEN EXCLUDED.school_name <> '' THEN EXCLUDED.school_name ELSE public.device_shikho_accounts.school_name END,
        current_class = CASE WHEN EXCLUDED.current_class <> '' THEN EXCLUDED.current_class ELSE public.device_shikho_accounts.current_class END,
        current_group = CASE WHEN EXCLUDED.current_group <> '' THEN EXCLUDED.current_group ELSE public.device_shikho_accounts.current_group END,
        current_batch = CASE WHEN EXCLUDED.current_batch <> '' THEN EXCLUDED.current_batch ELSE public.device_shikho_accounts.current_batch END,
        last_seen_at = now();

    -- ২. যদি ডিভাইসে আগে থেকেই পারমিশন সেট করা থাকে কিন্তু কোনো বাউন্ড অ্যাকাউন্ট না থাকে, তবে প্রথম লগইন করা অ্যাকাউন্টের সাথে অটো-বাইন্ড করা
    SELECT EXISTS (
        SELECT 1 FROM public.device_shikho_accounts
        WHERE device_hash = TRIM(p_device_hash)
          AND (is_access_granted = true OR is_access_restricted = true)
    ) INTO v_has_any_granted;

    IF NOT v_has_any_granted THEN
        SELECT * INTO v_dev
        FROM public.devices
        WHERE device_hash = TRIM(p_device_hash)
        LIMIT 1;

        IF FOUND AND (
            v_dev.is_access_restricted = true
            OR COALESCE(array_length(v_dev.allowed_classes, 1), 0) > 0
            OR COALESCE(array_length(v_dev.allowed_programs, 1), 0) > 0
        ) AND COALESCE(TRIM(v_dev.bound_shikho_user_id), '') = '' THEN
            UPDATE public.device_shikho_accounts
            SET is_access_granted = true,
                is_access_restricted = true,
                allowed_classes = COALESCE(v_dev.allowed_classes, '{}'::TEXT[]),
                allowed_groups = COALESCE(v_dev.allowed_groups, '{}'::TEXT[]),
                allowed_batches = COALESCE(v_dev.allowed_batches, '{}'::TEXT[]),
                allowed_programs = COALESCE(v_dev.allowed_programs, '{}'::TEXT[])
            WHERE device_hash = TRIM(p_device_hash) AND shikho_user_id = v_uid;

            UPDATE public.devices
            SET bound_shikho_user_id = v_uid,
                bound_user_phone = COALESCE(TRIM(p_user_phone), '')
            WHERE device_hash = TRIM(p_device_hash);
        END IF;
    END IF;

    RETURN public.get_device_course_permissions(p_device_hash, p_display_device_id, '', v_uid, p_user_phone);
END;
$$;

-- ১৫.৬ অ্যাডমিন অ্যাপ থেকে একটি ডিভাইসের আন্ডারে নির্দিষ্ট শিখো অ্যাকাউন্টে কোর্স ও সিলেবাস পারমিশন সেট করার RPC
CREATE OR REPLACE FUNCTION public.admin_set_account_course_permissions(
    p_device_hash TEXT,
    p_shikho_user_id TEXT,
    p_user_phone TEXT DEFAULT '',
    p_is_access_granted BOOLEAN DEFAULT true,
    p_is_restricted BOOLEAN DEFAULT true,
    p_allowed_classes TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_groups TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_batches TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_programs TEXT[] DEFAULT '{}'::TEXT[],
    p_revoke_other_accounts BOOLEAN DEFAULT true
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    -- যদি অন্য সব অ্যাকাউন্ট থেকে এক্সেস সরিয়ে শুধু এই অ্যাকাউন্টকে এক্সেস দিতে চায়
    IF p_revoke_other_accounts = true AND p_is_access_granted = true THEN
        UPDATE public.device_shikho_accounts
        SET is_access_granted = false,
            is_access_restricted = false,
            allowed_classes = '{}'::TEXT[],
            allowed_groups = '{}'::TEXT[],
            allowed_batches = '{}'::TEXT[],
            allowed_programs = '{}'::TEXT[]
        WHERE (device_hash = p_device_hash OR display_device_id = p_device_hash)
          AND shikho_user_id <> p_shikho_user_id;
    END IF;

    -- নির্দিষ্ট শিখো অ্যাকাউন্টের পারমিশন আপডেট করা
    UPDATE public.device_shikho_accounts
    SET is_access_granted = COALESCE(p_is_access_granted, true),
        is_access_restricted = COALESCE(p_is_restricted, true),
        allowed_classes = COALESCE(p_allowed_classes, '{}'::TEXT[]),
        allowed_groups = COALESCE(p_allowed_groups, '{}'::TEXT[]),
        allowed_batches = COALESCE(p_allowed_batches, '{}'::TEXT[]),
        allowed_programs = COALESCE(p_allowed_programs, '{}'::TEXT[]),
        last_seen_at = now()
    WHERE (device_hash = p_device_hash OR display_device_id = p_device_hash)
      AND (shikho_user_id = p_shikho_user_id OR (NULLIF(TRIM(p_user_phone), '') IS NOT NULL AND user_phone = TRIM(p_user_phone)));

    -- পাশাপাশি public.devices টেবিলেও বাউন্ড অ্যাকাউন্ট হিসেবে সিঙ্ক রাখা
    IF p_is_access_granted = true THEN
        UPDATE public.devices
        SET is_access_restricted = COALESCE(p_is_restricted, true),
            bound_shikho_user_id = p_shikho_user_id,
            bound_user_phone = COALESCE(p_user_phone, ''),
            allowed_classes = COALESCE(p_allowed_classes, '{}'::TEXT[]),
            allowed_groups = COALESCE(p_allowed_groups, '{}'::TEXT[]),
            allowed_batches = COALESCE(p_allowed_batches, '{}'::TEXT[]),
            allowed_programs = COALESCE(p_allowed_programs, '{}'::TEXT[]),
            updated_at = now()
        WHERE device_hash = p_device_hash OR display_device_id = p_device_hash;
    END IF;

    RETURN json_build_object(
        'success', true,
        'message', 'নির্দিষ্ট শিখো অ্যাকাউন্টের জন্য সিলেবাস ও কোর্স এক্সেস সফলভাবে সেট করা হয়েছে!'
    );
END;
$$;

-- ১৫.৭ অ্যাডমিন অ্যাপ থেকে ডিভাইস-লেভেল পারমিশন আপডেট করার RPC (ব্যাকওয়ার্ড কম্প্যাটিবল)
CREATE OR REPLACE FUNCTION public.admin_set_device_course_permissions(
    p_device_hash TEXT,
    p_is_restricted BOOLEAN,
    p_allowed_classes TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_groups TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_batches TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_programs TEXT[] DEFAULT '{}'::TEXT[],
    p_shikho_user_id TEXT DEFAULT ''
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    IF NULLIF(TRIM(p_shikho_user_id), '') IS NOT NULL THEN
        RETURN public.admin_set_account_course_permissions(
            p_device_hash := p_device_hash,
            p_shikho_user_id := TRIM(p_shikho_user_id),
            p_user_phone := '',
            p_is_access_granted := p_is_restricted,
            p_is_restricted := p_is_restricted,
            p_allowed_classes := p_allowed_classes,
            p_allowed_groups := p_allowed_groups,
            p_allowed_batches := p_allowed_batches,
            p_allowed_programs := p_allowed_programs,
            p_revoke_other_accounts := true
        );
    END IF;

    UPDATE public.devices
    SET is_access_restricted = COALESCE(p_is_restricted, false),
        allowed_classes = COALESCE(p_allowed_classes, '{}'::TEXT[]),
        allowed_groups = COALESCE(p_allowed_groups, '{}'::TEXT[]),
        allowed_batches = COALESCE(p_allowed_batches, '{}'::TEXT[]),
        allowed_programs = COALESCE(p_allowed_programs, '{}'::TEXT[]),
        updated_at = now()
    WHERE device_hash = p_device_hash OR display_device_id = p_device_hash;

    UPDATE public.activated_devices
    SET is_access_restricted = COALESCE(p_is_restricted, false),
        allowed_classes = COALESCE(p_allowed_classes, '{}'::TEXT[]),
        allowed_groups = COALESCE(p_allowed_groups, '{}'::TEXT[]),
        allowed_batches = COALESCE(p_allowed_batches, '{}'::TEXT[]),
        allowed_programs = COALESCE(p_allowed_programs, '{}'::TEXT[])
    WHERE device_hash = p_device_hash;

    RETURN json_build_object(
        'success', true,
        'message', 'ডিভাইসের কোর্স ও সিলেবাস পারমিশন সফলভাবে আপডেট করা হয়েছে!'
    );
END;
$$;

GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;

-- ========================================================
-- ১৬. COURSE ACCESS REQUEST, CUSTOM PRICING, FLEXIBLE INSTALLMENTS (কিস্তি) & PAYMENT VERIFICATION SYSTEM
-- (লক ও ঝাপসা কোর্স, 'কোর্সে এক্সেস পান' রিকোয়েস্ট, স্টুডেন্ট অনুযায়ী দাম ও কিস্তি নির্ধারণ, মোবাইল নম্বর/টাকা/TrxID পেমেন্ট সাবমিশন, রিজেক্ট কারণ এবং কিস্তির মেয়াদ শেষ হলে অটোমেটিক এক্সেস বন্ধ হওয়ার সম্পূর্ণ সিস্টেম)
-- ========================================================

-- ১৬.১ অ্যাডমিনের পেমেন্ট নম্বর ও নির্দেশনা টেবিল (বিকাশ, নগদ, রকেট)
CREATE TABLE IF NOT EXISTS public.course_payment_settings (
    id INT PRIMARY KEY DEFAULT 1,
    bkash_number TEXT DEFAULT '01700000000',
    nagad_number TEXT DEFAULT '01700000000',
    rocket_number TEXT DEFAULT '',
    payment_instruction_bn TEXT DEFAULT 'উপরের যেকোনো নাম্বারে Send Money করে নিচের ফর্মে আপনার মোবাইল নম্বর, পাঠানো টাকার পরিমাণ এবং Transaction ID (TrxID) সঠিকভাবে প্রদান করুন।',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT single_row_payment_settings CHECK (id = 1)
);

INSERT INTO public.course_payment_settings (id, bkash_number, nagad_number, rocket_number)
VALUES (1, '01700000000', '01700000000', '')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.course_payment_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Allow all access course_payment_settings" ON public.course_payment_settings;
CREATE POLICY "Allow all access course_payment_settings" ON public.course_payment_settings
FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
GRANT ALL ON TABLE public.course_payment_settings TO anon, authenticated, service_role;

-- ১৬.২ স্টুডেন্ট ও ডিভাইস অনুযায়ী কোর্স এক্সেস রিকোয়েস্ট, কাস্টম দাম এবং কিস্তি ট্র্যাকিং টেবিল
CREATE TABLE IF NOT EXISTS public.course_access_enrollments (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_hash TEXT NOT NULL,
    display_device_id TEXT DEFAULT '',
    shikho_user_id TEXT NOT NULL,
    user_phone TEXT DEFAULT '',
    student_name TEXT DEFAULT '',
    program_id TEXT NOT NULL,
    program_title TEXT DEFAULT '',
    class_code TEXT DEFAULT '',
    group_code TEXT DEFAULT '',
    batch_id TEXT DEFAULT '',
    banner_url TEXT DEFAULT '',
    -- status values:
    -- 'REQUESTED'        -> স্টুডেন্ট 'কোর্সে এক্সেস পান' বাটনে ক্লিক করেছে (অ্যাডমিন এখনো দাম সেট করেনি)
    -- 'APPROVED_TO_BUY'  -> অ্যাডমিন এক্সেস দিয়েছে এবং দাম ও কিস্তি সেট করেছে (অ্যাপে ঝাপসা কেটে গিয়ে দাম ও কেনার অপশন শো করবে)
    -- 'PAYMENT_PENDING'  -> স্টুডেন্ট পেমেন্ট (মোবাইল নম্বর, টাকা, TrxID) জমা দিয়েছে, অ্যাডমিন এপ্রুভালের অপেক্ষায়
    -- 'PAYMENT_REJECTED' -> অ্যাডমিন পেমেন্ট রিজেক্ট করেছে (কারণসহ অ্যাপে শো করবে)
    -- 'ACTIVE'           -> পেমেন্ট এপ্রুভড (ফুল পেইড অথবা কিস্তি চলমান এবং মেয়াদ আছে — কোর্স সম্পূর্ণ আনলকড)
    -- 'OVERDUE'          -> কিস্তির মেয়াদ (next_due_date) পার হয়ে গেছে কিন্তু কিস্তি দেয়নি (কোর্স অটোমেটিক লক হয়ে গেছে!)
    -- 'REVOKED'          -> অ্যাডমিন এক্সেস বাতিল করেছে
    status TEXT NOT NULL DEFAULT 'REQUESTED',
    total_price NUMERIC(10, 2) DEFAULT 0,
    paid_amount NUMERIC(10, 2) DEFAULT 0,
    due_amount NUMERIC(10, 2) DEFAULT 0,
    allow_installments BOOLEAN DEFAULT true,
    installment_plans_json JSONB DEFAULT '[]'::jsonb,
    active_plan_id TEXT DEFAULT '',
    active_plan_title TEXT DEFAULT '',
    total_installments INT DEFAULT 1,
    completed_installments INT DEFAULT 0,
    installment_interval_days INT DEFAULT 7,
    next_installment_amount NUMERIC(10, 2) DEFAULT 0,
    next_due_date TIMESTAMP WITH TIME ZONE NULL,
    last_transaction_id TEXT DEFAULT '',
    last_payment_amount NUMERIC(10, 2) DEFAULT 0,
    last_sender_mobile TEXT DEFAULT '',
    last_payment_method TEXT DEFAULT '',
    pending_installment_tx_id TEXT DEFAULT '',
    pending_installment_amount NUMERIC(10, 2) DEFAULT 0,
    rejection_reason TEXT DEFAULT '',
    admin_note TEXT DEFAULT '',
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    approved_at TIMESTAMP WITH TIME ZONE NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE(device_hash, shikho_user_id, program_id)
);

CREATE INDEX IF NOT EXISTS idx_course_enrollments_device_user ON public.course_access_enrollments(device_hash, shikho_user_id);
CREATE INDEX IF NOT EXISTS idx_course_enrollments_status ON public.course_access_enrollments(status);
CREATE INDEX IF NOT EXISTS idx_course_enrollments_due_date ON public.course_access_enrollments(next_due_date);

ALTER TABLE public.course_access_enrollments ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Allow all access course_access_enrollments" ON public.course_access_enrollments;
CREATE POLICY "Allow all access course_access_enrollments" ON public.course_access_enrollments
FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
GRANT ALL ON TABLE public.course_access_enrollments TO anon, authenticated, service_role;

-- ১৬.৩ কোর্স পেমেন্ট ও কিস্তি ট্রানজেকশন সাবমিশন টেবিল
CREATE TABLE IF NOT EXISTS public.course_payment_transactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    enrollment_id UUID REFERENCES public.course_access_enrollments(id) ON DELETE CASCADE,
    device_hash TEXT NOT NULL,
    display_device_id TEXT DEFAULT '',
    shikho_user_id TEXT NOT NULL,
    user_phone TEXT DEFAULT '',
    student_name TEXT DEFAULT '',
    program_id TEXT NOT NULL,
    program_title TEXT DEFAULT '',
    payment_type TEXT DEFAULT 'INITIAL', -- 'INITIAL' (প্রথমবার ভর্তি) অথবা 'INSTALLMENT' (পরবর্তী কিস্তি)
    selected_plan_id TEXT DEFAULT 'full',
    selected_plan_title TEXT DEFAULT 'এককালীন সম্পূর্ণ পেমেন্ট',
    installment_number INT DEFAULT 1,
    total_installments INT DEFAULT 1,
    interval_days INT DEFAULT 7,
    payment_method TEXT NOT NULL DEFAULT 'bKash',
    sender_mobile TEXT NOT NULL,
    amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    transaction_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED'
    rejection_reason TEXT DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    reviewed_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_course_tx_device_user ON public.course_payment_transactions(device_hash, shikho_user_id);
CREATE INDEX IF NOT EXISTS idx_course_tx_status ON public.course_payment_transactions(status);
CREATE INDEX IF NOT EXISTS idx_course_tx_enrollment ON public.course_payment_transactions(enrollment_id);

ALTER TABLE public.course_payment_transactions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Allow all access course_payment_transactions" ON public.course_payment_transactions;
CREATE POLICY "Allow all access course_payment_transactions" ON public.course_payment_transactions
FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
GRANT ALL ON TABLE public.course_payment_transactions TO anon, authenticated, service_role;

-- ১৬.৪ কিস্তির নির্দিষ্ট সময় (next_due_date) পার হয়ে গেলে অটোমেটিক কোর্স এক্সেস অফ করার ফাংশন
CREATE OR REPLACE FUNCTION public.enforce_overdue_course_installments()
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_rec RECORD;
BEGIN
    FOR v_rec IN
        SELECT id, device_hash, shikho_user_id, program_id
        FROM public.course_access_enrollments
        WHERE status = 'ACTIVE'
          AND due_amount > 0
          AND next_due_date IS NOT NULL
          AND next_due_date < now()
    LOOP
        -- ১. এনরোলমেন্ট স্ট্যাটাস OVERDUE করে দেওয়া
        UPDATE public.course_access_enrollments
        SET status = 'OVERDUE',
            updated_at = now()
        WHERE id = v_rec.id;

        -- ২. যদি device_shikho_accounts বা devices টেবিলে ওই প্রোগ্রাম আইডি থাকে তবে সেখান থেকেও সরিয়ে দেওয়া যাতে হোমে আর শো না করে
        UPDATE public.device_shikho_accounts
        SET allowed_programs = array_remove(allowed_programs, v_rec.program_id)
        WHERE device_hash = v_rec.device_hash
          AND shikho_user_id = v_rec.shikho_user_id;

        UPDATE public.devices
        SET allowed_programs = array_remove(allowed_programs, v_rec.program_id)
        WHERE device_hash = v_rec.device_hash;
    END LOOP;
END;
$$;

-- ১৬.৫ স্টুডেন্ট অ্যাপ থেকে 'কোর্সে এক্সেস পান' রিকোয়েস্ট পাঠানোর RPC
CREATE OR REPLACE FUNCTION public.request_course_purchase_access(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_shikho_user_id TEXT DEFAULT '',
    p_user_phone TEXT DEFAULT '',
    p_student_name TEXT DEFAULT '',
    p_program_id TEXT DEFAULT '',
    p_program_title TEXT DEFAULT '',
    p_class_code TEXT DEFAULT '',
    p_group_code TEXT DEFAULT '',
    p_batch_id TEXT DEFAULT '',
    p_banner_url TEXT DEFAULT ''
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_uid TEXT := COALESCE(NULLIF(TRIM(p_shikho_user_id), ''), NULLIF(TRIM(p_user_phone), ''), 'guest');
    v_existing RECORD;
    v_enrollment_id UUID;
BEGIN
    IF COALESCE(TRIM(p_device_hash), '') = '' OR COALESCE(TRIM(p_program_id), '') = '' THEN
        RETURN json_build_object('success', false, 'message', 'ডিভাইস বা কোর্সের তথ্য পাওয়া যায়নি।');
    END IF;

    SELECT * INTO v_existing
    FROM public.course_access_enrollments
    WHERE device_hash = TRIM(p_device_hash)
      AND shikho_user_id = v_uid
      AND program_id = TRIM(p_program_id)
    LIMIT 1;

    IF FOUND THEN
        IF v_existing.status IN ('APPROVED_TO_BUY', 'ACTIVE', 'PAYMENT_PENDING', 'OVERDUE') THEN
            RETURN json_build_object(
                'success', true,
                'status', v_existing.status,
                'enrollment_id', v_existing.id,
                'message', 'আপনার কোর্স রিকোয়েস্ট ইতিমধ্যে প্রসেস করা হয়েছে।'
            );
        END IF;

        UPDATE public.course_access_enrollments
        SET status = 'REQUESTED',
            display_device_id = COALESCE(NULLIF(TRIM(p_display_device_id), ''), display_device_id),
            user_phone = COALESCE(NULLIF(TRIM(p_user_phone), ''), user_phone),
            student_name = COALESCE(NULLIF(TRIM(p_student_name), ''), student_name),
            program_title = COALESCE(NULLIF(TRIM(p_program_title), ''), program_title),
            class_code = COALESCE(NULLIF(TRIM(p_class_code), ''), class_code),
            group_code = COALESCE(NULLIF(TRIM(p_group_code), ''), group_code),
            batch_id = COALESCE(NULLIF(TRIM(p_batch_id), ''), batch_id),
            banner_url = COALESCE(NULLIF(TRIM(p_banner_url), ''), banner_url),
            rejection_reason = '',
            requested_at = now(),
            updated_at = now()
        WHERE id = v_existing.id
        RETURNING id INTO v_enrollment_id;
    ELSE
        INSERT INTO public.course_access_enrollments (
            device_hash, display_device_id, shikho_user_id, user_phone, student_name,
            program_id, program_title, class_code, group_code, batch_id, banner_url,
            status, requested_at, updated_at
        ) VALUES (
            TRIM(p_device_hash),
            COALESCE(TRIM(p_display_device_id), ''),
            v_uid,
            COALESCE(TRIM(p_user_phone), ''),
            COALESCE(TRIM(p_student_name), ''),
            TRIM(p_program_id),
            COALESCE(TRIM(p_program_title), ''),
            COALESCE(TRIM(p_class_code), ''),
            COALESCE(TRIM(p_group_code), ''),
            COALESCE(TRIM(p_batch_id), ''),
            COALESCE(TRIM(p_banner_url), ''),
            'REQUESTED',
            now(),
            now()
        ) RETURNING id INTO v_enrollment_id;
    END IF;

    RETURN json_build_object(
        'success', true,
        'status', 'REQUESTED',
        'enrollment_id', v_enrollment_id,
        'message', 'অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন দাম ও কিস্তি নির্ধারণ করে এক্সেস দিলে এখানে দাম দেখতে পাবেন।'
    );
END;
$$;

-- ১৬.৬ অ্যাডমিন অ্যাপ থেকে স্টুডেন্টের কোর্স এক্সেস রিকোয়েস্ট এপ্রুভ করা এবং দাম ও কিস্তি (যেমন ৫ বারে ২০০ টাকা বা ২ বারে ৫০০ টাকা) সেট করার RPC
CREATE OR REPLACE FUNCTION public.admin_approve_course_access_request(
    p_enrollment_id UUID DEFAULT NULL,
    p_device_hash TEXT DEFAULT '',
    p_shikho_user_id TEXT DEFAULT '',
    p_program_id TEXT DEFAULT '',
    p_total_price NUMERIC DEFAULT 1000,
    p_allow_installments BOOLEAN DEFAULT true,
    p_installment_plans_json JSONB DEFAULT NULL,
    p_admin_note TEXT DEFAULT '',
    p_grant_direct_active BOOLEAN DEFAULT false
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_plans JSONB;
    v_price NUMERIC := GREATEST(0, COALESCE(p_total_price, 1000));
    v_part5 NUMERIC := ROUND(v_price / 5.0, 0);
    v_part2 NUMERIC := ROUND(v_price / 2.0, 0);
    v_part3 NUMERIC := ROUND(v_price / 3.0, 0);
    v_target_id UUID := p_enrollment_id;
    v_new_status TEXT;
BEGIN
    IF p_installment_plans_json IS NOT NULL AND jsonb_typeof(p_installment_plans_json) = 'array' AND jsonb_array_length(p_installment_plans_json) > 0 THEN
        v_plans := p_installment_plans_json;
    ELSE
        IF COALESCE(p_allow_installments, true) = true AND v_price > 0 THEN
            v_plans := jsonb_build_array(
                jsonb_build_object(
                    'plan_id', 'full',
                    'title_bn', 'এককালীন সম্পূর্ণ পেমেন্ট (৳' || v_price || ')',
                    'installments_count', 1,
                    'interval_days', 0,
                    'first_amount', v_price,
                    'per_installment_amount', v_price
                ),
                jsonb_build_object(
                    'plan_id', 'inst_2_weekly',
                    'title_bn', '২ কিস্তিতে পরিশোধ (প্রথমে ৳' || v_part2 || ' + পরে ৳' || (v_price - v_part2) || ')',
                    'installments_count', 2,
                    'interval_days', 15,
                    'first_amount', v_part2,
                    'per_installment_amount', (v_price - v_part2)
                ),
                jsonb_build_object(
                    'plan_id', 'inst_5_weekly',
                    'title_bn', '৫ কিস্তিতে পরিশোধ (প্রতি সপ্তাহে ৳' || v_part5 || ' করে)',
                    'installments_count', 5,
                    'interval_days', 7,
                    'first_amount', v_part5,
                    'per_installment_amount', v_part5
                ),
                jsonb_build_object(
                    'plan_id', 'inst_3_monthly',
                    'title_bn', '৩ কিস্তিতে পরিশোধ (প্রতি ১০ দিন পরপর ৳' || v_part3 || ' করে)',
                    'installments_count', 3,
                    'interval_days', 10,
                    'first_amount', v_part3,
                    'per_installment_amount', v_part3
                )
            );
        ELSE
            v_plans := jsonb_build_array(
                jsonb_build_object(
                    'plan_id', 'full',
                    'title_bn', 'এককালীন সম্পূর্ণ পেমেন্ট (৳' || v_price || ')',
                    'installments_count', 1,
                    'interval_days', 0,
                    'first_amount', v_price,
                    'per_installment_amount', v_price
                )
            );
        END IF;
    END IF;

    v_new_status := CASE WHEN COALESCE(p_grant_direct_active, false) = true OR v_price <= 0 THEN 'ACTIVE' ELSE 'APPROVED_TO_BUY' END;

    IF v_target_id IS NULL THEN
        SELECT id INTO v_target_id
        FROM public.course_access_enrollments
        WHERE (device_hash = TRIM(p_device_hash) OR display_device_id = TRIM(p_device_hash))
          AND (TRIM(p_shikho_user_id) = '' OR shikho_user_id = TRIM(p_shikho_user_id))
          AND program_id = TRIM(p_program_id)
        ORDER BY updated_at DESC
        LIMIT 1;
    END IF;

    IF v_target_id IS NOT NULL THEN
        UPDATE public.course_access_enrollments
        SET status = v_new_status,
            total_price = v_price,
            due_amount = CASE WHEN v_new_status = 'ACTIVE' THEN 0 ELSE GREATEST(0, v_price - paid_amount) END,
            allow_installments = COALESCE(p_allow_installments, true),
            installment_plans_json = v_plans,
            admin_note = COALESCE(p_admin_note, ''),
            rejection_reason = '',
            approved_at = now(),
            updated_at = now()
        WHERE id = v_target_id;
    ELSIF COALESCE(TRIM(p_device_hash), '') <> '' AND COALESCE(TRIM(p_program_id), '') <> '' THEN
        INSERT INTO public.course_access_enrollments (
            device_hash, shikho_user_id, program_id, status,
            total_price, paid_amount, due_amount, allow_installments,
            installment_plans_json, admin_note, approved_at, updated_at
        ) VALUES (
            TRIM(p_device_hash),
            COALESCE(NULLIF(TRIM(p_shikho_user_id), ''), 'guest'),
            TRIM(p_program_id),
            v_new_status,
            v_price,
            CASE WHEN v_new_status = 'ACTIVE' THEN v_price ELSE 0 END,
            CASE WHEN v_new_status = 'ACTIVE' THEN 0 ELSE v_price END,
            COALESCE(p_allow_installments, true),
            v_plans,
            COALESCE(p_admin_note, ''),
            now(),
            now()
        ) RETURNING id INTO v_target_id;
    ELSE
        RETURN json_build_object('success', false, 'message', 'এনরোলমেন্ট রিকোয়েস্ট পাওয়া যায়নি।');
    END IF;

    RETURN json_build_object(
        'success', true,
        'enrollment_id', v_target_id,
        'status', v_new_status,
        'total_price', v_price,
        'message', 'কোর্স কেনার এক্সেস, দাম ও কিস্তি প্ল্যান সফলভাবে সেট করা হয়েছে!'
    );
END;
$$;

-- ১৬.৭ স্টুডেন্ট অ্যাপ থেকে পেমেন্ট বা কিস্তির ট্রানজেকশন (মোবাইল নম্বর, পাঠানো টাকা, TrxID) জমা দেওয়ার RPC
CREATE OR REPLACE FUNCTION public.submit_course_payment_transaction(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_shikho_user_id TEXT DEFAULT '',
    p_user_phone TEXT DEFAULT '',
    p_student_name TEXT DEFAULT '',
    p_program_id TEXT DEFAULT '',
    p_program_title TEXT DEFAULT '',
    p_payment_method TEXT DEFAULT 'bKash',
    p_sender_mobile TEXT DEFAULT '',
    p_amount NUMERIC DEFAULT 0,
    p_transaction_id TEXT DEFAULT '',
    p_selected_plan_id TEXT DEFAULT 'full',
    p_selected_plan_title TEXT DEFAULT 'এককালীন সম্পূর্ণ পেমেন্ট',
    p_total_installments INT DEFAULT 1,
    p_interval_days INT DEFAULT 7
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_uid TEXT := COALESCE(NULLIF(TRIM(p_shikho_user_id), ''), NULLIF(TRIM(p_user_phone), ''), 'guest');
    v_enr RECORD;
    v_enrollment_id UUID;
    v_tx_id UUID;
    v_inst_num INT := 1;
    v_pay_type TEXT := 'INITIAL';
BEGIN
    IF COALESCE(TRIM(p_sender_mobile), '') = '' THEN
        RETURN json_build_object('success', false, 'message', 'যে মোবাইল নম্বর থেকে টাকা পাঠিয়েছেন সেটি লিখুন।');
    END IF;
    IF COALESCE(p_amount, 0) <= 0 THEN
        RETURN json_build_object('success', false, 'message', 'পাঠানো টাকার সঠিক পরিমাণ লিখুন।');
    END IF;
    IF COALESCE(TRIM(p_transaction_id), '') = '' THEN
        RETURN json_build_object('success', false, 'message', 'সঠিক ট্রানজেকশন আইডি (TrxID) লিখুন।');
    END IF;

    SELECT * INTO v_enr
    FROM public.course_access_enrollments
    WHERE device_hash = TRIM(p_device_hash)
      AND shikho_user_id = v_uid
      AND program_id = TRIM(p_program_id)
    LIMIT 1;

    IF FOUND THEN
        v_enrollment_id := v_enr.id;
        v_inst_num := COALESCE(v_enr.completed_installments, 0) + 1;
        v_pay_type := CASE WHEN COALESCE(v_enr.completed_installments, 0) > 0 THEN 'INSTALLMENT' ELSE 'INITIAL' END;

        UPDATE public.course_access_enrollments
        SET status = CASE
                WHEN v_enr.status = 'ACTIVE' AND (v_enr.next_due_date IS NULL OR v_enr.next_due_date > now()) THEN 'ACTIVE'
                ELSE 'PAYMENT_PENDING'
            END,
            active_plan_id = CASE WHEN COALESCE(v_enr.active_plan_id, '') = '' THEN COALESCE(p_selected_plan_id, 'full') ELSE v_enr.active_plan_id END,
            active_plan_title = CASE WHEN COALESCE(v_enr.active_plan_title, '') = '' THEN COALESCE(p_selected_plan_title, 'এককালীন পেমেন্ট') ELSE v_enr.active_plan_title END,
            total_installments = CASE WHEN COALESCE(v_enr.completed_installments, 0) = 0 THEN GREATEST(1, COALESCE(p_total_installments, 1)) ELSE v_enr.total_installments END,
            installment_interval_days = CASE WHEN COALESCE(v_enr.completed_installments, 0) = 0 THEN GREATEST(0, COALESCE(p_interval_days, 7)) ELSE v_enr.installment_interval_days END,
            last_transaction_id = UPPER(TRIM(p_transaction_id)),
            last_payment_amount = p_amount,
            last_sender_mobile = TRIM(p_sender_mobile),
            last_payment_method = COALESCE(TRIM(p_payment_method), 'bKash'),
            pending_installment_tx_id = UPPER(TRIM(p_transaction_id)),
            pending_installment_amount = p_amount,
            rejection_reason = '',
            updated_at = now()
        WHERE id = v_enrollment_id;
    ELSE
        INSERT INTO public.course_access_enrollments (
            device_hash, display_device_id, shikho_user_id, user_phone, student_name,
            program_id, program_title, status, total_price, paid_amount, due_amount,
            active_plan_id, active_plan_title, total_installments, completed_installments,
            installment_interval_days, last_transaction_id, last_payment_amount,
            last_sender_mobile, last_payment_method, pending_installment_tx_id, pending_installment_amount,
            requested_at, updated_at
        ) VALUES (
            TRIM(p_device_hash),
            COALESCE(TRIM(p_display_device_id), ''),
            v_uid,
            COALESCE(TRIM(p_user_phone), ''),
            COALESCE(TRIM(p_student_name), ''),
            TRIM(p_program_id),
            COALESCE(TRIM(p_program_title), ''),
            'PAYMENT_PENDING',
            p_amount,
            0,
            p_amount,
            COALESCE(p_selected_plan_id, 'full'),
            COALESCE(p_selected_plan_title, 'এককালীন পেমেন্ট'),
            GREATEST(1, COALESCE(p_total_installments, 1)),
            0,
            GREATEST(0, COALESCE(p_interval_days, 7)),
            UPPER(TRIM(p_transaction_id)),
            p_amount,
            TRIM(p_sender_mobile),
            COALESCE(TRIM(p_payment_method), 'bKash'),
            UPPER(TRIM(p_transaction_id)),
            p_amount,
            now(),
            now()
        ) RETURNING id INTO v_enrollment_id;
    END IF;

    INSERT INTO public.course_payment_transactions (
        enrollment_id, device_hash, display_device_id, shikho_user_id, user_phone, student_name,
        program_id, program_title, payment_type, selected_plan_id, selected_plan_title,
        installment_number, total_installments, interval_days,
        payment_method, sender_mobile, amount, transaction_id, status, created_at
    ) VALUES (
        v_enrollment_id,
        TRIM(p_device_hash),
        COALESCE(TRIM(p_display_device_id), ''),
        v_uid,
        COALESCE(TRIM(p_user_phone), ''),
        COALESCE(TRIM(p_student_name), ''),
        TRIM(p_program_id),
        COALESCE(TRIM(p_program_title), ''),
        v_pay_type,
        COALESCE(p_selected_plan_id, 'full'),
        COALESCE(p_selected_plan_title, 'এককালীন পেমেন্ট'),
        v_inst_num,
        GREATEST(1, COALESCE(p_total_installments, 1)),
        GREATEST(0, COALESCE(p_interval_days, 7)),
        COALESCE(TRIM(p_payment_method), 'bKash'),
        TRIM(p_sender_mobile),
        p_amount,
        UPPER(TRIM(p_transaction_id)),
        'PENDING',
        now()
    ) RETURNING id INTO v_tx_id;

    RETURN json_build_object(
        'success', true,
        'transaction_id', v_tx_id,
        'enrollment_id', v_enrollment_id,
        'message', 'আপনার পেমেন্ট তথ্য সফলভাবে জমা হয়েছে! অ্যাডমিন যাচাই করে এপ্রুভ করলেই কোর্সটি আনলক হয়ে যাবে।'
    );
END;
$$;

-- ১৬.৮ অ্যাডমিন অ্যাপ থেকে পেমেন্ট এপ্রুভ (Approve) অথবা কারণসহ রিজেক্ট (Reject) করার RPC
CREATE OR REPLACE FUNCTION public.admin_review_course_payment(
    p_transaction_id UUID,
    p_action TEXT, -- 'APPROVE' অথবা 'REJECT'
    p_rejection_reason TEXT DEFAULT '',
    p_next_due_days INT DEFAULT NULL,
    p_custom_next_installment_amount NUMERIC DEFAULT NULL
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_tx RECORD;
    v_enr RECORD;
    v_new_paid NUMERIC;
    v_total_price NUMERIC;
    v_new_due NUMERIC;
    v_new_completed INT;
    v_remaining_inst INT;
    v_interval INT;
    v_next_due TIMESTAMP WITH TIME ZONE;
    v_next_inst_amt NUMERIC;
BEGIN
    SELECT * INTO v_tx
    FROM public.course_payment_transactions
    WHERE id = p_transaction_id
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN json_build_object('success', false, 'message', 'পেমেন্ট ট্রানজেকশন খুঁজে পাওয়া যায়নি।');
    END IF;

    SELECT * INTO v_enr
    FROM public.course_access_enrollments
    WHERE id = v_tx.enrollment_id
    LIMIT 1;

    IF UPPER(TRIM(p_action)) = 'APPROVE' THEN
        UPDATE public.course_payment_transactions
        SET status = 'APPROVED',
            rejection_reason = '',
            reviewed_at = now()
        WHERE id = p_transaction_id;

        IF FOUND AND v_enr.id IS NOT NULL THEN
            v_total_price := CASE WHEN COALESCE(v_enr.total_price, 0) > 0 THEN v_enr.total_price ELSE v_tx.amount END;
            v_new_paid := COALESCE(v_enr.paid_amount, 0) + COALESCE(v_tx.amount, 0);
            v_new_due := GREATEST(0, v_total_price - v_new_paid);
            v_new_completed := COALESCE(v_enr.completed_installments, 0) + 1;
            v_interval := COALESCE(p_next_due_days, NULLIF(v_enr.installment_interval_days, 0), NULLIF(v_tx.interval_days, 0), 7);

            IF v_new_due > 0 THEN
                v_next_due := now() + (v_interval || ' days')::INTERVAL;
                v_remaining_inst := GREATEST(1, COALESCE(v_enr.total_installments, v_tx.total_installments, 2) - v_new_completed);
                v_next_inst_amt := COALESCE(p_custom_next_installment_amount, ROUND(v_new_due / v_remaining_inst, 0));
            ELSE
                v_next_due := NULL;
                v_next_inst_amt := 0;
            END IF;

            UPDATE public.course_access_enrollments
            SET status = 'ACTIVE',
                total_price = v_total_price,
                paid_amount = v_new_paid,
                due_amount = v_new_due,
                completed_installments = v_new_completed,
                installment_interval_days = v_interval,
                next_installment_amount = v_next_inst_amt,
                next_due_date = v_next_due,
                pending_installment_tx_id = '',
                pending_installment_amount = 0,
                rejection_reason = '',
                approved_at = COALESCE(approved_at, now()),
                updated_at = now()
            WHERE id = v_enr.id;
        END IF;

        RETURN json_build_object(
            'success', true,
            'status', 'ACTIVE',
            'paid_amount', v_new_paid,
            'due_amount', v_new_due,
            'next_due_date', v_next_due,
            'message', 'পেমেন্ট সফলভাবে এপ্রুভ করা হয়েছে এবং স্টুডেন্টের জন্য কোর্সটি আনলক হয়েছে!'
        );

    ELSIF UPPER(TRIM(p_action)) = 'REJECT' THEN
        UPDATE public.course_payment_transactions
        SET status = 'REJECTED',
            rejection_reason = COALESCE(NULLIF(TRIM(p_rejection_reason), ''), 'প্রদত্ত পেমেন্ট তথ্য বা ট্রানজেকশন আইডি সঠিক নয়।'),
            reviewed_at = now()
        WHERE id = p_transaction_id;

        IF v_enr.id IS NOT NULL THEN
            UPDATE public.course_access_enrollments
            SET status = CASE
                    WHEN v_enr.status = 'ACTIVE' AND (v_enr.next_due_date IS NULL OR v_enr.next_due_date > now()) THEN 'ACTIVE'
                    WHEN v_enr.status = 'OVERDUE' OR (v_enr.due_amount > 0 AND v_enr.next_due_date IS NOT NULL AND v_enr.next_due_date < now()) THEN 'OVERDUE'
                    ELSE 'PAYMENT_REJECTED'
                END,
                pending_installment_tx_id = '',
                pending_installment_amount = 0,
                rejection_reason = COALESCE(NULLIF(TRIM(p_rejection_reason), ''), 'প্রদত্ত পেমেন্ট তথ্য বা ট্রানজেকশন আইডি সঠিক নয়।'),
                updated_at = now()
            WHERE id = v_enr.id;
        END IF;

        RETURN json_build_object(
            'success', true,
            'status', 'REJECTED',
            'rejection_reason', COALESCE(NULLIF(TRIM(p_rejection_reason), ''), 'প্রদত্ত পেমেন্ট তথ্য বা ট্রানজেকশন আইডি সঠিক নয়।'),
            'message', 'পেমেন্ট রিকোয়েস্ট রিজেক্ট করা হয়েছে এবং স্টুডেন্টের কাছে কারণ পাঠানো হয়েছে।'
        );
    ELSE
        RETURN json_build_object('success', false, 'message', 'অকার্যকর অ্যাকশন। APPROVE অথবা REJECT পাঠান।');
    END IF;
END;
$$;

-- ১৬.৯ স্টুডেন্ট অ্যাপ থেকে তার সকল কোর্সের এক্সেস, দাম, কিস্তি ও পেমেন্ট স্ট্যাটাস একসাথে পাওয়ার RPC
CREATE OR REPLACE FUNCTION public.get_student_course_commerce_status(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_shikho_user_id TEXT DEFAULT '',
    p_user_phone TEXT DEFAULT ''
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_uid TEXT := COALESCE(NULLIF(TRIM(p_shikho_user_id), ''), NULLIF(TRIM(p_user_phone), ''), '');
    v_settings JSON;
    v_enrollments JSON;
    v_transactions JSON;
BEGIN
    PERFORM public.enforce_overdue_course_installments();

    SELECT row_to_json(s) INTO v_settings
    FROM (
        SELECT bkash_number, nagad_number, rocket_number, payment_instruction_bn
        FROM public.course_payment_settings
        WHERE id = 1
        LIMIT 1
    ) s;

    SELECT COALESCE(json_agg(row_to_json(e) ORDER BY e.updated_at DESC), '[]'::json) INTO v_enrollments
    FROM (
        SELECT *
        FROM public.course_access_enrollments
        WHERE (device_hash = TRIM(p_device_hash) OR (NULLIF(TRIM(p_display_device_id), '') IS NOT NULL AND display_device_id = TRIM(p_display_device_id)))
          AND (
              v_uid = ''
              OR shikho_user_id = v_uid
              OR (NULLIF(TRIM(p_user_phone), '') IS NOT NULL AND user_phone = TRIM(p_user_phone))
          )
    ) e;

    SELECT COALESCE(json_agg(row_to_json(t) ORDER BY t.created_at DESC), '[]'::json) INTO v_transactions
    FROM (
        SELECT *
        FROM public.course_payment_transactions
        WHERE (device_hash = TRIM(p_device_hash) OR (NULLIF(TRIM(p_display_device_id), '') IS NOT NULL AND display_device_id = TRIM(p_display_device_id)))
          AND (
              v_uid = ''
              OR shikho_user_id = v_uid
              OR (NULLIF(TRIM(p_user_phone), '') IS NOT NULL AND user_phone = TRIM(p_user_phone))
          )
        LIMIT 30
    ) t;

    RETURN json_build_object(
        'success', true,
        'payment_settings', COALESCE(v_settings, json_build_object('bkash_number', '01700000000', 'nagad_number', '01700000000', 'rocket_number', '', 'payment_instruction_bn', 'উপরের নাম্বারে Send Money করে নিচের ফর্মে মোবাইল নম্বর, টাকার পরিমাণ এবং TrxID দিন।')),
        'enrollments', COALESCE(v_enrollments, '[]'::json),
        'transactions', COALESCE(v_transactions, '[]'::json)
    );
END;
$$;

GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;



