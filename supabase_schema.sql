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
-- ১৫. DEVICE-SPECIFIC GRANULAR SYLLABUS & COURSE ACCESS CONTROL
-- (অ্যাডমিন অ্যাপ থেকে যেকোনো ডিভাইস সিলেক্ট করে নির্দিষ্ট শ্রেণি, বিভাগ, ব্যাচ এবং কোর্স লক/এক্সেস দেওয়ার সিস্টেম)
-- ========================================================

-- ১৫.১ বিদ্যমান টেবিলগুলোতে নতুন পারমিশন কলাম যুক্ত করা
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS is_access_restricted BOOLEAN DEFAULT false;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_classes TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_groups TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_batches TEXT[] DEFAULT '{}'::TEXT[];
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allowed_programs TEXT[] DEFAULT '{}'::TEXT[];

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

-- ১৫.২ কোর্স ক্যাটালগ টেবিল (যাতে অ্যাডমিন অ্যাপে ড্রপডাউন/চেকবক্সে সব কোর্সের নাম ও আইডি দেখা যায়)
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

-- ১৫.৩ স্টুডেন্ট অ্যাপের জন্য ডিভাইসের কোর্স পারমিশন রিড করার RPC
CREATE OR REPLACE FUNCTION public.get_device_course_permissions(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_code TEXT DEFAULT ''
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_dev RECORD;
    v_code RECORD;
    v_act RECORD;
BEGIN
    -- ১. প্রথমে public.devices টেবিল চেক করা (ডিভাইস-লেভেল পারমিশন সর্বোচ্চ অগ্রাধিকার পাবে)
    SELECT is_access_restricted, allowed_classes, allowed_groups, allowed_batches, allowed_programs
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
        RETURN json_build_object(
            'is_access_restricted', true,
            'allowed_classes', COALESCE(v_dev.allowed_classes, '{}'::TEXT[]),
            'allowed_groups', COALESCE(v_dev.allowed_groups, '{}'::TEXT[]),
            'allowed_batches', COALESCE(v_dev.allowed_batches, '{}'::TEXT[]),
            'allowed_programs', COALESCE(v_dev.allowed_programs, '{}'::TEXT[])
        );
    END IF;

    -- ২. যদি ডিভাইসে না থাকে, তবে ব্যবহৃত access_codes টেবিল চেক করা
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
                'allowed_classes', COALESCE(v_code.allowed_classes, '{}'::TEXT[]),
                'allowed_groups', COALESCE(v_code.allowed_groups, '{}'::TEXT[]),
                'allowed_batches', COALESCE(v_code.allowed_batches, '{}'::TEXT[]),
                'allowed_programs', COALESCE(v_code.allowed_programs, '{}'::TEXT[])
            );
        END IF;
    END IF;

    -- ৩. activated_devices টেবিল চেক করা
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
            'allowed_classes', COALESCE(v_act.allowed_classes, '{}'::TEXT[]),
            'allowed_groups', COALESCE(v_act.allowed_groups, '{}'::TEXT[]),
            'allowed_batches', COALESCE(v_act.allowed_batches, '{}'::TEXT[]),
            'allowed_programs', COALESCE(v_act.allowed_programs, '{}'::TEXT[])
        );
    END IF;

    RETURN json_build_object(
        'is_access_restricted', false,
        'allowed_classes', '{}'::TEXT[],
        'allowed_groups', '{}'::TEXT[],
        'allowed_batches', '{}'::TEXT[],
        'allowed_programs', '{}'::TEXT[]
    );
END;
$$;

-- ১৫.৪ অ্যাডমিন অ্যাপ থেকে ডিভাইসের কোর্স পারমিশন সেট/আপডেট করার RPC
CREATE OR REPLACE FUNCTION public.admin_set_device_course_permissions(
    p_device_hash TEXT,
    p_is_restricted BOOLEAN,
    p_allowed_classes TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_groups TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_batches TEXT[] DEFAULT '{}'::TEXT[],
    p_allowed_programs TEXT[] DEFAULT '{}'::TEXT[]
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
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

