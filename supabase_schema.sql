-- ========================================================
-- MAXBIRD EXCLUSIVE DEVICE ACCESS & ACTIVATION SYSTEM
-- Supabase SQL Schema & Atomic RPC Stored Functions
-- 
-- নির্দেশিকা:
-- ১. আপনার Supabase Dashboard (https://supabase.com) এ যান।
-- ২. বাম পাশের মেনু থেকে "SQL Editor" এ ক্লিক করুন।
-- ৩. "New query" বাটনে ক্লিক করে নিচের সম্পূর্ণ কোড পেস্ট করে "Run" চাপুন।
-- ৪. নতুন কোড তৈরি করতে "Table Editor" এ গিয়ে 'access_codes' টেবিলে নতুন সারি (Row) যোগ করুন।
-- ========================================================

-- ১. এক্সেস কোড টেবিল (অ্যাডমিন যে কোড তৈরি করবেন)
CREATE TABLE IF NOT EXISTS public.access_codes (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code TEXT UNIQUE NOT NULL,
    student_name TEXT,
    max_devices INT DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- ২. নিবন্ধিত ডিভাইস টেবিল (যে যে ফোনে কোড দিয়ে এক্টিভেট হয়েছে)
CREATE TABLE IF NOT EXISTS public.activated_devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code_id UUID REFERENCES public.access_codes(id) ON DELETE CASCADE,
    device_hash TEXT NOT NULL,
    app_signature TEXT,
    session_token TEXT NOT NULL,
    is_blocked BOOLEAN DEFAULT false,
    activated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    last_seen TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE(code_id, device_hash)
);

-- সিকিউরিটি: Row Level Security (RLS) সক্রিয়করণ (সরাসরি টেবিল এক্সেস ব্লক, শুধু RPC দিয়ে এক্সেস হবে)
ALTER TABLE public.access_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activated_devices ENABLE ROW LEVEL SECURITY;

-- ৩. ডিভাইস অ্যাক্টিভেশন স্টোরড ফাংশন (Atomic Activation RPC)
CREATE OR REPLACE FUNCTION public.activate_device(
    p_code TEXT,
    p_device_hash TEXT,
    p_app_signature TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_code_record RECORD;
    v_bound_count INT;
    v_existing_device RECORD;
    v_new_token TEXT := gen_random_uuid()::TEXT;
BEGIN
    -- ৩.১ কোড পরীক্ষা
    SELECT * INTO v_code_record FROM public.access_codes WHERE UPPER(code) = UPPER(TRIM(p_code));
    IF NOT FOUND THEN
        RETURN json_build_object('success', false, 'message', 'ভুল এক্সেস কোড। সঠিক কোড দিন।');
    END IF;

    IF NOT v_code_record.is_active THEN
        RETURN json_build_object('success', false, 'message', 'এই এক্সেস কোডটি অ্যাডমিন দ্বারা নিষ্ক্রিয় করা হয়েছে।');
    END IF;

    IF v_code_record.expires_at IS NOT NULL AND v_code_record.expires_at < now() THEN
        RETURN json_build_object('success', false, 'message', 'এক্সেস কোডের মেয়াদ শেষ হয়ে গেছে।');
    END IF;

    -- ৩.২ ডিভাইসটি কি আগে থেকেই এই কোডে নিবন্ধিত?
    SELECT * INTO v_existing_device FROM public.activated_devices
    WHERE code_id = v_code_record.id AND device_hash = p_device_hash;

    IF FOUND THEN
        IF v_existing_device.is_blocked THEN
            RETURN json_build_object('success', false, 'message', 'এই ডিভাইসটি ব্লক করা হয়েছে। অ্যাডমিনের সাথে যোগাযোগ করুন।');
        END IF;
        UPDATE public.activated_devices 
        SET session_token = v_new_token, last_seen = now(), app_signature = p_app_signature
        WHERE id = v_existing_device.id;
        RETURN json_build_object('success', true, 'token', v_new_token, 'message', 'ডিভাইস সফলভাবে পুনরায় যাচাই করা হয়েছে।');
    END IF;

    -- ৩.৩ ডিভাইস কোটা সীমা পরীক্ষা (১টি কোড যেন অন্য ফোনে শেয়ার না করা যায়)
    SELECT count(*) INTO v_bound_count FROM public.activated_devices WHERE code_id = v_code_record.id;
    IF v_bound_count >= v_code_record.max_devices THEN
        RETURN json_build_object('success', false, 'message', 'এই কোডটি ইতিমধ্যে অন্য ফোনে ব্যবহার করা হয়েছে। ডিভাইস লিমিট শেষ।');
    END IF;

    -- ৩.৪ নতুন ডিভাইস বাইন্ড ও টোকেন প্রদান
    INSERT INTO public.activated_devices (code_id, device_hash, app_signature, session_token)
    VALUES (v_code_record.id, p_device_hash, p_app_signature, v_new_token);

    RETURN json_build_object('success', true, 'token', v_new_token, 'message', 'ডিভাইস এক্সেস সফলভাবে সক্রিয় হয়েছে!');
END;
$$;

-- ৪. হার্টবিট ভেরিফিকেশন স্টোরড ফাংশন (Fast Heartbeat Verification RPC)
CREATE OR REPLACE FUNCTION public.verify_device_heartbeat(
    p_device_hash TEXT,
    p_token TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_device RECORD;
    v_code RECORD;
BEGIN
    SELECT * INTO v_device FROM public.activated_devices 
    WHERE device_hash = p_device_hash AND session_token = p_token;

    IF NOT FOUND THEN
        RETURN json_build_object('valid', false, 'reason', 'SESSION_NOT_FOUND');
    END IF;

    IF v_device.is_blocked THEN
        RETURN json_build_object('valid', false, 'reason', 'DEVICE_BLOCKED');
    END IF;

    SELECT * INTO v_code FROM public.access_codes WHERE id = v_device.code_id;
    IF NOT FOUND OR NOT v_code.is_active THEN
        RETURN json_build_object('valid', false, 'reason', 'CODE_REVOKED');
    END IF;

    UPDATE public.activated_devices SET last_seen = now() WHERE id = v_device.id;
    RETURN json_build_object('valid', true);
END;
$$;

-- ৫. উদাহরণ: পরীক্ষামূলক একটি কোড যোগ করতে চাইলে নিচের লাইনটি চালাতে পারেন:
-- INSERT INTO public.access_codes (code, student_name, max_devices) VALUES ('VIP-STUDENT-01', 'Fahim Mia', 1);
