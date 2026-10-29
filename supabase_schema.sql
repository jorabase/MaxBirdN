-- ========================================================
-- MAXBIRD PRODUCTION SUPABASE SQL SCHEMA & ATOMIC RPCs
-- 100% Synced with Student App & Admin App Production Schema
-- ========================================================

BEGIN;

-- ০. পূর্বের সকল ওভারলোডেড ফাংশন স্বয়ংক্রিয়ভাবে ড্রপ করা (Fixes PGRST203 Candidate Function Overload Error)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT oid::regprocedure AS func_signature
        FROM pg_proc
        WHERE proname IN (
            'activate_device',
            'check_device_status',
            'check_student_verification_status',
            'request_student_verification',
            'verify_device_heartbeat',
            'admin_toggle_device_ban',
            'create_access_code',
            'upsert_user_gemini_api_key',
            'get_user_gemini_api_key',
            'request_course_purchase_access',
            'submit_course_payment_transaction',
            'admin_approve_course_access_request',
            'admin_review_course_payment',
            'get_student_course_commerce_status',
            'enforce_overdue_course_installments'
        )
          AND pronamespace = 'public'::regnamespace
    ) LOOP
        EXECUTE 'DROP FUNCTION IF EXISTS ' || r.func_signature || ' CASCADE';
    END LOOP;
END $$;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ========================================================
-- ১. public.access_codes
-- ========================================================
CREATE TABLE IF NOT EXISTS public.access_codes (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    student_name text DEFAULT ''::text,
    max_devices integer DEFAULT 1,
    is_active boolean DEFAULT true,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now(),
    name text DEFAULT ''::text,
    device_limit integer DEFAULT 1,
    status text DEFAULT 'active'::text,
    duration_days integer DEFAULT 30,
    validity_days integer DEFAULT 30,
    expiry_date timestamp with time zone,
    note text DEFAULT ''::text,
    batch text DEFAULT ''::text,
    updated_at timestamp with time zone DEFAULT now(),
    can_view_profiles boolean DEFAULT false,
    is_access_restricted boolean DEFAULT false,
    allowed_classes text[] DEFAULT '{}'::text[],
    allowed_groups text[] DEFAULT '{}'::text[],
    allowed_batches text[] DEFAULT '{}'::text[],
    allowed_programs text[] DEFAULT '{}'::text[],
    CONSTRAINT access_codes_pkey PRIMARY KEY (id)
);

-- ========================================================
-- ২. public.activated_devices
-- ========================================================
CREATE TABLE IF NOT EXISTS public.activated_devices (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    code_id uuid REFERENCES public.access_codes(id) ON DELETE CASCADE,
    device_hash text NOT NULL,
    app_signature text,
    session_token text NOT NULL,
    is_blocked boolean DEFAULT false,
    activated_at timestamp with time zone DEFAULT now(),
    last_seen timestamp with time zone DEFAULT now(),
    can_view_profiles boolean DEFAULT false,
    is_access_restricted boolean DEFAULT false,
    allowed_classes text[] DEFAULT '{}'::text[],
    allowed_groups text[] DEFAULT '{}'::text[],
    allowed_batches text[] DEFAULT '{}'::text[],
    allowed_programs text[] DEFAULT '{}'::text[],
    CONSTRAINT activated_devices_pkey PRIMARY KEY (id),
    CONSTRAINT activated_devices_code_id_device_hash_key UNIQUE (code_id, device_hash)
);

-- ========================================================
-- ৩. public.devices
-- ========================================================
CREATE TABLE IF NOT EXISTS public.devices (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    device_hash text NOT NULL UNIQUE,
    display_device_id text NOT NULL,
    device_model text,
    device_brand text,
    device_manufacturer text,
    android_version text,
    sdk_version integer,
    app_version text,
    applied_code text,
    is_banned boolean DEFAULT false,
    ban_reason text DEFAULT 'অ্যাডমিন কর্তৃক ব্যান করা হয়েছে'::text,
    banned_at timestamp with time zone,
    status text DEFAULT 'active'::text,
    last_seen timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    can_view_profiles boolean DEFAULT false,
    is_access_restricted boolean DEFAULT false,
    bound_shikho_user_id text DEFAULT ''::text,
    bound_user_phone text DEFAULT ''::text,
    allowed_classes text[] DEFAULT '{}'::text[],
    allowed_groups text[] DEFAULT '{}'::text[],
    allowed_batches text[] DEFAULT '{}'::text[],
    allowed_programs text[] DEFAULT '{}'::text[],
    shikho_access_token text DEFAULT ''::text,
    shikho_refresh_token text DEFAULT ''::text,
    CONSTRAINT devices_pkey PRIMARY KEY (id)
);

-- ========================================================
-- ৪. public.activation_support_links
-- ========================================================
CREATE TABLE IF NOT EXISTS public.activation_support_links (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    title text NOT NULL,
    subtitle text DEFAULT ''::text,
    icon_type text NOT NULL DEFAULT 'telegram'::text,
    url text NOT NULL,
    color_hex text DEFAULT '#229ED9'::text,
    is_active boolean NOT NULL DEFAULT true,
    priority integer NOT NULL DEFAULT 1,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT activation_support_links_pkey PRIMARY KEY (id)
);

-- ========================================================
-- ৫. public.app_notices
-- ========================================================
CREATE TABLE IF NOT EXISTS public.app_notices (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    title text NOT NULL,
    description text NOT NULL,
    image_url text,
    action_url text,
    action_button_text text DEFAULT 'বিস্তারিত দেখুন'::text,
    priority integer DEFAULT 0,
    is_active boolean DEFAULT true,
    show_as_popup boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT app_notices_pkey PRIMARY KEY (id)
);

-- ========================================================
-- ৬. public.app_updates
-- ========================================================
CREATE TABLE IF NOT EXISTS public.app_updates (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    latest_version_name text NOT NULL DEFAULT '1.0.0'::text,
    latest_version_code integer NOT NULL DEFAULT 1,
    min_supported_version_code integer NOT NULL DEFAULT 1,
    is_force_update boolean NOT NULL DEFAULT false,
    title text NOT NULL DEFAULT 'নতুন আপডেট এসেছে!'::text,
    changelog text NOT NULL DEFAULT 'অ্যাপের পারফরম্যান্স ও নতুন ফিচার যুক্ত করা হয়েছে।'::text,
    download_url text NOT NULL,
    button_text text NOT NULL DEFAULT 'এখনই আপডেট করুন'::text,
    apk_checksum_sha256 text,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT app_updates_pkey PRIMARY KEY (id)
);

-- ========================================================
-- ৭. CLEANUP: DROP LEGACY AI TABLES (যদি পূর্বের কোনো AI টেবিল থাকে তা মুছে ফেলা)
-- ========================================================
DROP TABLE IF EXISTS public.user_gemini_api_keys CASCADE;
DROP TABLE IF EXISTS public.user_ai_settings CASCADE;

-- ========================================================
-- ৮. public.course_payment_settings
-- ========================================================
CREATE TABLE IF NOT EXISTS public.course_payment_settings (
    id integer NOT NULL DEFAULT 1 CHECK (id = 1),
    bkash_number text DEFAULT '01700000000'::text,
    nagad_number text DEFAULT '01700000000'::text,
    rocket_number text DEFAULT ''::text,
    payment_instructions_bn text DEFAULT 'উপরের যেকোনো নাম্বারে Send Money করে নিচের ফর্মে আপনার মোবাইল নম্বর, পাঠানো টাকার পরিমাণ এবং Transaction ID (TrxID) সঠিকভাবে প্রদান করুন।'::text,
    default_course_price integer DEFAULT 1000,
    updated_at timestamp with time zone DEFAULT now(),
    payment_instruction_bn text DEFAULT 'উপরের যেকোনো নাম্বারে Send Money করে নিচের ফর্মে আপনার মোবাইল নম্বর, পাঠানো টাকার পরিমাণ এবং Transaction ID (TrxID) সঠিকভাবে প্রদান করুন।'::text,
    CONSTRAINT course_payment_settings_pkey PRIMARY KEY (id)
);

INSERT INTO public.course_payment_settings (id, bkash_number, nagad_number, rocket_number)
VALUES (1, '01700000000', '01700000000', '')
ON CONFLICT (id) DO NOTHING;

-- ========================================================
-- ৯. public.course_access_enrollments
-- ========================================================
CREATE TABLE IF NOT EXISTS public.course_access_enrollments (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    device_id text NOT NULL,
    shikho_account_key text NOT NULL DEFAULT ''::text,
    student_name text DEFAULT ''::text,
    student_phone text DEFAULT ''::text,
    student_class text DEFAULT ''::text,
    student_group text DEFAULT ''::text,
    program_id text NOT NULL,
    program_title text NOT NULL DEFAULT ''::text,
    class_code text DEFAULT ''::text,
    group_code text DEFAULT ''::text,
    batch_id text DEFAULT ''::text,
    status text NOT NULL DEFAULT 'REQUESTED'::text,
    total_price integer NOT NULL DEFAULT 1000,
    paid_amount integer NOT NULL DEFAULT 0,
    due_amount integer NOT NULL DEFAULT 1000,
    installment_enabled boolean NOT NULL DEFAULT false,
    selected_plan_id text DEFAULT 'FULL'::text,
    selected_plan_title text DEFAULT 'একবারে সম্পূর্ণ পেমেন্ট'::text,
    total_installments integer NOT NULL DEFAULT 1,
    completed_installments integer NOT NULL DEFAULT 0,
    next_installment_amount integer NOT NULL DEFAULT 0,
    installment_interval_days integer NOT NULL DEFAULT 30,
    next_due_date timestamp with time zone,
    installment_plans_json jsonb DEFAULT '[]'::jsonb,
    rejection_reason text DEFAULT ''::text,
    admin_note text DEFAULT ''::text,
    requested_at timestamp with time zone DEFAULT now(),
    approved_at timestamp with time zone,
    unlocked_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now(),
    device_hash text DEFAULT ''::text,
    display_device_id text DEFAULT ''::text,
    shikho_user_id text DEFAULT ''::text,
    user_phone text DEFAULT ''::text,
    banner_url text DEFAULT ''::text,
    allow_installments boolean DEFAULT true,
    active_plan_id text DEFAULT 'FULL'::text,
    active_plan_title text DEFAULT 'এককালীন সম্পূর্ণ পেমেন্ট'::text,
    last_transaction_id text DEFAULT ''::text,
    last_payment_amount numeric DEFAULT 0,
    last_sender_mobile text DEFAULT ''::text,
    last_payment_method text DEFAULT ''::text,
    pending_installment_tx_id text DEFAULT ''::text,
    pending_installment_amount numeric DEFAULT 0,
    CONSTRAINT course_access_enrollments_pkey PRIMARY KEY (id),
    CONSTRAINT course_access_enrollments_device_id_shikho_account_key_prog_key UNIQUE (device_id, shikho_account_key, program_id)
);

-- ========================================================
-- ১০. public.course_payment_transactions
-- ========================================================
CREATE TABLE IF NOT EXISTS public.course_payment_transactions (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    enrollment_id bigint REFERENCES public.course_access_enrollments(id) ON DELETE CASCADE,
    device_id text NOT NULL,
    shikho_account_key text DEFAULT ''::text,
    student_name text DEFAULT ''::text,
    program_id text NOT NULL,
    program_title text DEFAULT ''::text,
    plan_id text DEFAULT 'FULL'::text,
    plan_title text DEFAULT ''::text,
    installment_number integer DEFAULT 1,
    payment_method text NOT NULL,
    sender_mobile text NOT NULL,
    amount integer NOT NULL DEFAULT 0,
    transaction_id text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING'::text,
    rejection_reason text DEFAULT ''::text,
    admin_note text DEFAULT ''::text,
    submitted_at timestamp with time zone DEFAULT now(),
    reviewed_at timestamp with time zone,
    device_hash text DEFAULT ''::text,
    display_device_id text DEFAULT ''::text,
    shikho_user_id text DEFAULT ''::text,
    user_phone text DEFAULT ''::text,
    payment_type text DEFAULT 'INITIAL'::text,
    selected_plan_id text DEFAULT 'FULL'::text,
    selected_plan_title text DEFAULT ''::text,
    interval_days integer DEFAULT 7,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT course_payment_transactions_pkey PRIMARY KEY (id)
);

-- ========================================================
-- ১১. RLS & PERMISSIONS
-- ========================================================
ALTER TABLE public.access_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activated_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activation_support_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_notices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_updates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_payment_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_access_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.course_payment_transactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow all for access_codes" ON public.access_codes;
CREATE POLICY "Allow all for access_codes" ON public.access_codes FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow all for activated_devices" ON public.activated_devices;
CREATE POLICY "Allow all for activated_devices" ON public.activated_devices FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full admin operations on devices" ON public.devices;
CREATE POLICY "Allow full admin operations on devices" ON public.devices FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full admin support links" ON public.activation_support_links;
CREATE POLICY "Allow full admin support links" ON public.activation_support_links FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow all access app_notices" ON public.app_notices;
CREATE POLICY "Allow all access app_notices" ON public.app_notices FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow all access app_updates" ON public.app_updates;
CREATE POLICY "Allow all access app_updates" ON public.app_updates FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow all access course_payment_settings" ON public.course_payment_settings;
CREATE POLICY "Allow all access course_payment_settings" ON public.course_payment_settings FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow all access course_access_enrollments" ON public.course_access_enrollments;
CREATE POLICY "Allow all access course_access_enrollments" ON public.course_access_enrollments FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow all access course_payment_transactions" ON public.course_payment_transactions;
CREATE POLICY "Allow all access course_payment_transactions" ON public.course_payment_transactions FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;

-- ========================================================
-- ১২. STUDENT VERIFICATION & DEVICE SECURITY RPCs
-- ========================================================

-- ১২.১ স্টুডেন্ট ভেরিফিকেশন স্ট্যাটাস চেক RPC
CREATE OR REPLACE FUNCTION public.check_student_verification_status(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_device_model TEXT DEFAULT '',
    p_device_brand TEXT DEFAULT '',
    p_android_version TEXT DEFAULT '',
    p_app_version TEXT DEFAULT ''
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_dev RECORD;
    v_act RECORD;
BEGIN
    SELECT * INTO v_dev
    FROM public.devices
    WHERE device_hash = TRIM(p_device_hash)
       OR (NULLIF(TRIM(p_display_device_id), '') IS NOT NULL AND display_device_id = TRIM(p_display_device_id))
    ORDER BY updated_at DESC
    LIMIT 1;

    IF FOUND THEN
        UPDATE public.devices
        SET last_seen = now()
        WHERE id = v_dev.id;

        IF COALESCE(v_dev.is_banned, false) = true OR LOWER(COALESCE(v_dev.status, '')) IN ('banned', 'blocked', 'rejected') THEN
            RETURN jsonb_build_object(
                'is_banned', true,
                'status', 'banned',
                'ban_reason', COALESCE(NULLIF(v_dev.ban_reason, ''), 'এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।')
            );
        END IF;

        IF LOWER(COALESCE(v_dev.status, '')) IN ('active', 'approved') THEN
            RETURN jsonb_build_object(
                'is_banned', false,
                'is_approved', true,
                'status', 'active',
                'applied_code', COALESCE(v_dev.applied_code, 'VERIFIED-DEVICE'),
                'session_token', 'VERIFIED-' || TRIM(p_device_hash)
            );
        END IF;

        IF LOWER(COALESCE(v_dev.status, '')) IN ('pending', 'requested') THEN
            RETURN jsonb_build_object(
                'is_banned', false,
                'is_approved', false,
                'status', 'pending',
                'student_note', REPLACE(COALESCE(v_dev.applied_code, ''), 'REQ: ', '')
            );
        END IF;
    END IF;

    SELECT * INTO v_act
    FROM public.activated_devices
    WHERE device_hash = TRIM(p_device_hash)
    ORDER BY activated_at DESC
    LIMIT 1;

    IF FOUND THEN
        IF COALESCE(v_act.is_blocked, false) = true THEN
            RETURN jsonb_build_object(
                'is_banned', true,
                'status', 'banned',
                'ban_reason', 'এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।'
            );
        END IF;
        RETURN jsonb_build_object(
            'is_banned', false,
            'is_approved', true,
            'status', 'active',
            'session_token', COALESCE(v_act.session_token, 'VERIFIED-' || TRIM(p_device_hash))
        );
    END IF;

    RETURN jsonb_build_object(
        'is_banned', false,
        'is_approved', false,
        'status', 'unverified'
    );
END;
$$;

-- ১২.২ ওয়ান-ক্লিক স্টুডেন্ট ভেরিফিকেশন আবেদন RPC
CREATE OR REPLACE FUNCTION public.request_student_verification(
    p_device_hash TEXT,
    p_display_device_id TEXT DEFAULT '',
    p_device_model TEXT DEFAULT '',
    p_device_brand TEXT DEFAULT '',
    p_android_version TEXT DEFAULT '',
    p_app_version TEXT DEFAULT '',
    p_student_note TEXT DEFAULT '',
    p_app_signature TEXT DEFAULT ''
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_existing RECORD;
    v_note TEXT := CASE WHEN NULLIF(TRIM(p_student_note), '') IS NOT NULL THEN 'REQ: ' || TRIM(p_student_note) ELSE 'STUDENT-VERIFY' END;
BEGIN
    SELECT * INTO v_existing
    FROM public.devices
    WHERE device_hash = TRIM(p_device_hash)
    LIMIT 1;

    IF FOUND THEN
        IF COALESCE(v_existing.is_banned, false) = true OR LOWER(COALESCE(v_existing.status, '')) IN ('banned', 'blocked') THEN
            RETURN jsonb_build_object(
                'is_banned', true,
                'status', 'banned',
                'ban_reason', COALESCE(NULLIF(v_existing.ban_reason, ''), 'এই ডিভাইসে প্রবেশাধিকার স্থগিত করা হয়েছে।')
            );
        END IF;

        IF LOWER(COALESCE(v_existing.status, '')) IN ('active', 'approved') THEN
            RETURN jsonb_build_object(
                'is_banned', false,
                'is_approved', true,
                'status', 'active',
                'session_token', 'VERIFIED-' || TRIM(p_device_hash)
            );
        END IF;
    END IF;

    INSERT INTO public.devices (
        device_hash,
        display_device_id,
        device_model,
        device_brand,
        android_version,
        app_version,
        applied_code,
        status,
        is_banned,
        last_seen,
        updated_at
    ) VALUES (
        TRIM(p_device_hash),
        COALESCE(NULLIF(TRIM(p_display_device_id), ''), 'UNKNOWN'),
        p_device_model,
        p_device_brand,
        p_android_version,
        p_app_version,
        v_note,
        'pending',
        false,
        now(),
        now()
    )
    ON CONFLICT (device_hash) DO UPDATE SET
        display_device_id = CASE WHEN EXCLUDED.display_device_id <> '' AND EXCLUDED.display_device_id <> 'UNKNOWN' THEN EXCLUDED.display_device_id ELSE public.devices.display_device_id END,
        device_model = COALESCE(NULLIF(EXCLUDED.device_model, ''), public.devices.device_model),
        device_brand = COALESCE(NULLIF(EXCLUDED.device_brand, ''), public.devices.device_brand),
        android_version = COALESCE(NULLIF(EXCLUDED.android_version, ''), public.devices.android_version),
        app_version = COALESCE(NULLIF(EXCLUDED.app_version, ''), public.devices.app_version),
        applied_code = CASE WHEN NULLIF(TRIM(p_student_note), '') IS NOT NULL THEN v_note ELSE COALESCE(public.devices.applied_code, v_note) END,
        status = CASE WHEN LOWER(COALESCE(public.devices.status, '')) IN ('active', 'approved') THEN 'active' ELSE 'pending' END,
        last_seen = now(),
        updated_at = now();

    RETURN jsonb_build_object(
        'is_banned', false,
        'status', 'pending',
        'message', 'আপনার স্টুডেন্ট ভেরিফিকেশন আবেদন সফলভাবে জমা হয়েছে।'
    );
END;
$$;

-- ১২.৩ হার্টবিট ভেরিফিকেশন স্টোরড ফাংশন (Supports both one-click verified devices and code-activated devices)
CREATE OR REPLACE FUNCTION public.verify_device_heartbeat(
    p_device_hash TEXT,
    p_token TEXT
) RETURNS JSON LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_dev RECORD;
    v_act RECORD;
    v_code RECORD;
BEGIN
    SELECT * INTO v_dev FROM public.devices WHERE device_hash = p_device_hash LIMIT 1;
    IF FOUND THEN
        IF COALESCE(v_dev.is_banned, false) = true OR LOWER(COALESCE(v_dev.status, '')) IN ('banned', 'blocked', 'rejected') THEN
            RETURN json_build_object('valid', false, 'reason', COALESCE(v_dev.ban_reason, 'DEVICE_BANNED'));
        END IF;

        IF LOWER(COALESCE(v_dev.status, '')) IN ('active', 'approved') THEN
            UPDATE public.devices SET last_seen = now() WHERE id = v_dev.id;
            RETURN json_build_object('valid', true);
        END IF;
    END IF;

    SELECT * INTO v_act FROM public.activated_devices
    WHERE device_hash = p_device_hash AND session_token = p_token
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN json_build_object('valid', false, 'reason', 'SESSION_NOT_FOUND');
    END IF;

    IF v_act.is_blocked THEN
        RETURN json_build_object('valid', false, 'reason', 'DEVICE_BLOCKED');
    END IF;

    SELECT * INTO v_code FROM public.access_codes WHERE id = v_act.code_id;
    IF FOUND AND v_code.is_active IS FALSE THEN
        RETURN json_build_object('valid', false, 'reason', 'CODE_REVOKED');
    END IF;

    UPDATE public.activated_devices SET last_seen = now() WHERE id = v_act.id;
    UPDATE public.devices SET last_seen = now() WHERE device_hash = p_device_hash;
    RETURN json_build_object('valid', true);
END;
$$;

-- ========================================================
-- ১৪. COURSE COMMERCE, INSTALLMENTS & PHONE-BOUND ACCESS RPCs
-- (Strictly uses BIGINT ids and ON CONFLICT (device_id, shikho_account_key, program_id))
-- ========================================================

CREATE OR REPLACE FUNCTION public.enforce_overdue_course_installments()
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    UPDATE public.course_access_enrollments
    SET status = 'OVERDUE',
        updated_at = now()
    WHERE status = 'ACTIVE'
      AND due_amount > 0
      AND next_due_date IS NOT NULL
      AND next_due_date < now();
END;
$$;

CREATE OR REPLACE FUNCTION public.request_course_purchase_access(
    p_device_id TEXT DEFAULT '',
    p_shikho_account_key TEXT DEFAULT '',
    p_student_name TEXT DEFAULT '',
    p_student_phone TEXT DEFAULT '',
    p_student_class TEXT DEFAULT '',
    p_student_group TEXT DEFAULT '',
    p_program_id TEXT DEFAULT '',
    p_program_title TEXT DEFAULT ''
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_account_key TEXT := COALESCE(
        NULLIF(TRIM(p_student_phone), ''),
        NULLIF(TRIM(p_shikho_account_key), ''),
        NULLIF(TRIM(p_device_id), '')
    );
    v_existing RECORD;
    v_enr_id BIGINT;
BEGIN
    IF COALESCE(v_account_key, '') = '' OR COALESCE(TRIM(p_program_id), '') = '' THEN
        RETURN jsonb_build_object('success', false, 'message', 'একাউন্ট বা কোর্সের তথ্য পাওয়া যায়নি।');
    END IF;

    SELECT * INTO v_existing
    FROM public.course_access_enrollments
    WHERE program_id = TRIM(p_program_id)
      AND (
          shikho_account_key = v_account_key
          OR device_id = v_account_key
          OR (NULLIF(TRIM(p_student_phone), '') IS NOT NULL AND (student_phone = TRIM(p_student_phone) OR user_phone = TRIM(p_student_phone)))
      )
    ORDER BY updated_at DESC
    LIMIT 1;

    IF FOUND THEN
        v_enr_id := v_existing.id;
        UPDATE public.course_access_enrollments
        SET student_name = COALESCE(NULLIF(TRIM(p_student_name), ''), student_name),
            student_phone = COALESCE(NULLIF(TRIM(p_student_phone), ''), student_phone),
            user_phone = COALESCE(NULLIF(TRIM(p_student_phone), ''), user_phone),
            student_class = COALESCE(NULLIF(TRIM(p_student_class), ''), student_class),
            student_group = COALESCE(NULLIF(TRIM(p_student_group), ''), student_group),
            class_code = COALESCE(NULLIF(TRIM(p_student_class), ''), class_code),
            group_code = COALESCE(NULLIF(TRIM(p_student_group), ''), group_code),
            program_title = COALESCE(NULLIF(TRIM(p_program_title), ''), program_title),
            status = CASE
                WHEN v_existing.status IN ('APPROVED_TO_BUY', 'ACTIVE', 'PAYMENT_PENDING', 'OVERDUE') THEN v_existing.status
                ELSE 'REQUESTED'
            END,
            rejection_reason = CASE
                WHEN v_existing.status IN ('APPROVED_TO_BUY', 'ACTIVE', 'PAYMENT_PENDING', 'OVERDUE') THEN v_existing.rejection_reason
                ELSE ''
            END,
            updated_at = now()
        WHERE id = v_enr_id;
    ELSE
        INSERT INTO public.course_access_enrollments (
            device_id, device_hash, display_device_id,
            shikho_account_key, shikho_user_id,
            student_name, student_phone, user_phone,
            student_class, student_group, class_code, group_code,
            program_id, program_title, status, requested_at, updated_at
        ) VALUES (
            v_account_key, v_account_key, v_account_key,
            v_account_key, v_account_key,
            COALESCE(TRIM(p_student_name), 'শিক্ষার্থী'),
            COALESCE(TRIM(p_student_phone), v_account_key),
            COALESCE(TRIM(p_student_phone), v_account_key),
            COALESCE(TRIM(p_student_class), ''),
            COALESCE(TRIM(p_student_group), ''),
            COALESCE(TRIM(p_student_class), ''),
            COALESCE(TRIM(p_student_group), ''),
            TRIM(p_program_id),
            COALESCE(TRIM(p_program_title), ''),
            'REQUESTED',
            now(),
            now()
        )
        ON CONFLICT (device_id, shikho_account_key, program_id) DO UPDATE SET
            student_name = COALESCE(NULLIF(EXCLUDED.student_name, ''), public.course_access_enrollments.student_name),
            student_phone = COALESCE(NULLIF(EXCLUDED.student_phone, ''), public.course_access_enrollments.student_phone),
            user_phone = COALESCE(NULLIF(EXCLUDED.user_phone, ''), public.course_access_enrollments.user_phone),
            program_title = COALESCE(NULLIF(EXCLUDED.program_title, ''), public.course_access_enrollments.program_title),
            status = CASE
                WHEN public.course_access_enrollments.status IN ('APPROVED_TO_BUY', 'ACTIVE', 'PAYMENT_PENDING', 'OVERDUE')
                    THEN public.course_access_enrollments.status
                ELSE 'REQUESTED'
            END,
            updated_at = now()
        RETURNING id INTO v_enr_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'status', 'REQUESTED',
        'enrollment_id', v_enr_id,
        'message', 'অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন দাম সেট করে এক্সেস দিলে এখানে দেখতে পাবেন।'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_course_payment_transaction(
    p_device_id TEXT DEFAULT '',
    p_shikho_account_key TEXT DEFAULT '',
    p_student_name TEXT DEFAULT '',
    p_program_id TEXT DEFAULT '',
    p_program_title TEXT DEFAULT '',
    p_plan_id TEXT DEFAULT 'FULL',
    p_plan_title TEXT DEFAULT 'একবারে সম্পূর্ণ পেমেন্ট',
    p_total_installments INT DEFAULT 1,
    p_installment_interval_days INT DEFAULT 30,
    p_payment_method TEXT DEFAULT 'bKash',
    p_sender_mobile TEXT DEFAULT '',
    p_amount INT DEFAULT 0,
    p_transaction_id TEXT DEFAULT ''
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_account_key TEXT := COALESCE(
        NULLIF(TRIM(p_shikho_account_key), ''),
        NULLIF(TRIM(p_device_id), '')
    );
    v_existing RECORD;
    v_enr_id BIGINT;
    v_completed INT := 0;
    v_pay_type TEXT := 'INITIAL';
BEGIN
    IF COALESCE(TRIM(p_sender_mobile), '') = '' OR COALESCE(p_amount, 0) <= 0 OR COALESCE(TRIM(p_transaction_id), '') = '' THEN
        RETURN jsonb_build_object('success', false, 'message', 'সঠিক মোবাইল নম্বর, টাকার পরিমাণ এবং ট্রানজেকশন আইডি দিন।');
    END IF;

    SELECT * INTO v_existing
    FROM public.course_access_enrollments
    WHERE program_id = TRIM(p_program_id)
      AND (
          shikho_account_key = v_account_key
          OR device_id = v_account_key
          OR student_phone = v_account_key
          OR user_phone = v_account_key
      )
    ORDER BY updated_at DESC
    LIMIT 1;

    IF FOUND THEN
        v_enr_id := v_existing.id;
        v_completed := COALESCE(v_existing.completed_installments, 0);
        v_pay_type := CASE WHEN v_completed > 0 THEN 'INSTALLMENT' ELSE 'INITIAL' END;

        UPDATE public.course_access_enrollments
        SET status = CASE
                WHEN v_existing.status = 'ACTIVE' AND (v_existing.next_due_date IS NULL OR v_existing.next_due_date > now()) THEN 'ACTIVE'
                ELSE 'PAYMENT_PENDING'
            END,
            selected_plan_id = COALESCE(NULLIF(TRIM(p_plan_id), ''), selected_plan_id),
            selected_plan_title = COALESCE(NULLIF(TRIM(p_plan_title), ''), selected_plan_title),
            active_plan_id = COALESCE(NULLIF(TRIM(p_plan_id), ''), active_plan_id),
            active_plan_title = COALESCE(NULLIF(TRIM(p_plan_title), ''), active_plan_title),
            total_installments = CASE WHEN v_completed = 0 THEN GREATEST(1, COALESCE(p_total_installments, 1)) ELSE total_installments END,
            installment_interval_days = CASE WHEN v_completed = 0 THEN GREATEST(1, COALESCE(p_installment_interval_days, 30)) ELSE installment_interval_days END,
            last_transaction_id = UPPER(TRIM(p_transaction_id)),
            last_payment_amount = p_amount,
            last_sender_mobile = TRIM(p_sender_mobile),
            last_payment_method = COALESCE(TRIM(p_payment_method), 'bKash'),
            pending_installment_tx_id = UPPER(TRIM(p_transaction_id)),
            pending_installment_amount = p_amount,
            rejection_reason = '',
            updated_at = now()
        WHERE id = v_enr_id;
    ELSE
        INSERT INTO public.course_access_enrollments (
            device_id, device_hash, display_device_id,
            shikho_account_key, shikho_user_id,
            student_name, student_phone, user_phone,
            program_id, program_title, status,
            total_price, paid_amount, due_amount,
            selected_plan_id, selected_plan_title, active_plan_id, active_plan_title,
            total_installments, completed_installments, installment_interval_days,
            last_transaction_id, last_payment_amount, last_sender_mobile, last_payment_method,
            pending_installment_tx_id, pending_installment_amount,
            rejection_reason, requested_at, updated_at
        ) VALUES (
            v_account_key, v_account_key, v_account_key,
            v_account_key, v_account_key,
            COALESCE(TRIM(p_student_name), 'শিক্ষার্থী'),
            v_account_key, v_account_key,
            TRIM(p_program_id), COALESCE(TRIM(p_program_title), ''),
            'PAYMENT_PENDING',
            GREATEST(1, p_amount), 0, GREATEST(1, p_amount),
            COALESCE(p_plan_id, 'FULL'), COALESCE(p_plan_title, 'একবারে সম্পূর্ণ পেমেন্ট'),
            COALESCE(p_plan_id, 'FULL'), COALESCE(p_plan_title, 'একবারে সম্পূর্ণ পেমেন্ট'),
            GREATEST(1, COALESCE(p_total_installments, 1)), 0, GREATEST(1, COALESCE(p_installment_interval_days, 30)),
            UPPER(TRIM(p_transaction_id)), p_amount, TRIM(p_sender_mobile), COALESCE(TRIM(p_payment_method), 'bKash'),
            UPPER(TRIM(p_transaction_id)), p_amount,
            '', now(), now()
        )
        ON CONFLICT (device_id, shikho_account_key, program_id) DO UPDATE SET
            status = CASE
                WHEN public.course_access_enrollments.status = 'ACTIVE'
                     AND (public.course_access_enrollments.next_due_date IS NULL OR public.course_access_enrollments.next_due_date > now())
                    THEN 'ACTIVE'
                ELSE 'PAYMENT_PENDING'
            END,
            selected_plan_id = COALESCE(NULLIF(EXCLUDED.selected_plan_id, ''), public.course_access_enrollments.selected_plan_id),
            selected_plan_title = COALESCE(NULLIF(EXCLUDED.selected_plan_title, ''), public.course_access_enrollments.selected_plan_title),
            active_plan_id = COALESCE(NULLIF(EXCLUDED.active_plan_id, ''), public.course_access_enrollments.active_plan_id),
            active_plan_title = COALESCE(NULLIF(EXCLUDED.active_plan_title, ''), public.course_access_enrollments.active_plan_title),
            last_transaction_id = EXCLUDED.last_transaction_id,
            last_payment_amount = EXCLUDED.last_payment_amount,
            last_sender_mobile = EXCLUDED.last_sender_mobile,
            last_payment_method = EXCLUDED.last_payment_method,
            pending_installment_tx_id = EXCLUDED.pending_installment_tx_id,
            pending_installment_amount = EXCLUDED.pending_installment_amount,
            rejection_reason = '',
            updated_at = now()
        RETURNING id, completed_installments INTO v_enr_id, v_completed;
    END IF;

    INSERT INTO public.course_payment_transactions (
        enrollment_id, device_id, device_hash, display_device_id,
        shikho_account_key, shikho_user_id, user_phone, student_name,
        program_id, program_title, plan_id, plan_title, selected_plan_id, selected_plan_title,
        payment_type, installment_number, interval_days,
        payment_method, sender_mobile, amount, transaction_id, status, submitted_at, created_at
    ) VALUES (
        v_enr_id, v_account_key, v_account_key, v_account_key,
        v_account_key, v_account_key, v_account_key, COALESCE(p_student_name, 'শিক্ষার্থী'),
        TRIM(p_program_id), COALESCE(p_program_title, ''),
        COALESCE(p_plan_id, 'FULL'), COALESCE(p_plan_title, 'একবারে সম্পূর্ণ পেমেন্ট'),
        COALESCE(p_plan_id, 'FULL'), COALESCE(p_plan_title, 'একবারে সম্পূর্ণ পেমেন্ট'),
        v_pay_type, COALESCE(v_completed, 0) + 1, GREATEST(1, COALESCE(p_installment_interval_days, 30)),
        COALESCE(p_payment_method, 'bKash'), TRIM(p_sender_mobile),
        p_amount, UPPER(TRIM(p_transaction_id)), 'PENDING', now(), now()
    );

    RETURN jsonb_build_object(
        'success', true,
        'enrollment_id', v_enr_id,
        'message', 'আপনার পেমেন্ট তথ্য সফলভাবে জমা হয়েছে! অ্যাডমিন যাচাই করে এপ্রুভ করলেই কোর্সটি আনলক হয়ে যাবে।'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_student_course_commerce_status(
    p_device_id TEXT DEFAULT '',
    p_shikho_account_key TEXT DEFAULT '',
    p_shikho_user_id TEXT DEFAULT '',
    p_user_phone TEXT DEFAULT ''
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_phone TEXT := NULLIF(TRIM(p_user_phone), '');
    v_acct TEXT := COALESCE(NULLIF(TRIM(p_shikho_account_key), ''), NULLIF(TRIM(p_device_id), ''), v_phone, '');
    v_uid TEXT := NULLIF(TRIM(p_shikho_user_id), '');
    v_settings JSONB;
    v_enrollments JSONB;
    v_transactions JSONB;
BEGIN
    PERFORM public.enforce_overdue_course_installments();

    SELECT to_jsonb(s) INTO v_settings
    FROM (
        SELECT *
        FROM public.course_payment_settings
        WHERE id = 1
        LIMIT 1
    ) s;

    SELECT COALESCE(jsonb_agg(to_jsonb(e) ORDER BY e.updated_at DESC), '[]'::jsonb) INTO v_enrollments
    FROM (
        SELECT *
        FROM public.course_access_enrollments
        WHERE (v_phone IS NOT NULL AND (student_phone = v_phone OR user_phone = v_phone OR shikho_account_key = v_phone OR device_id = v_phone))
           OR (v_acct <> '' AND (shikho_account_key = v_acct OR device_id = v_acct OR student_phone = v_acct OR user_phone = v_acct))
           OR (v_uid IS NOT NULL AND (shikho_user_id = v_uid OR shikho_account_key = v_uid))
    ) e;

    SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY t.id DESC), '[]'::jsonb) INTO v_transactions
    FROM (
        SELECT *
        FROM public.course_payment_transactions
        WHERE (v_phone IS NOT NULL AND (user_phone = v_phone OR shikho_account_key = v_phone OR device_id = v_phone))
           OR (v_acct <> '' AND (shikho_account_key = v_acct OR device_id = v_acct OR user_phone = v_acct))
           OR (v_uid IS NOT NULL AND (shikho_user_id = v_uid OR shikho_account_key = v_uid))
        LIMIT 50
    ) t;

    RETURN jsonb_build_object(
        'success', true,
        'payment_settings', COALESCE(v_settings, '{}'::jsonb),
        'enrollments', COALESCE(v_enrollments, '[]'::jsonb),
        'transactions', COALESCE(v_transactions, '[]'::jsonb)
    );
END;
$$;

-- ========================================================
-- ১৫. COLUMN SYNC TRIGGER FOR course_access_enrollments
-- ========================================================
CREATE OR REPLACE FUNCTION public.sync_course_access_enrollment_columns()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF COALESCE(NEW.device_id, '') = '' AND COALESCE(NEW.device_hash, '') <> '' THEN
        NEW.device_id := NEW.device_hash;
    ELSIF COALESCE(NEW.device_hash, '') = '' AND COALESCE(NEW.device_id, '') <> '' THEN
        NEW.device_hash := NEW.device_id;
    END IF;

    IF COALESCE(NEW.shikho_account_key, '') = '' AND COALESCE(NEW.shikho_user_id, '') <> '' THEN
        NEW.shikho_account_key := NEW.shikho_user_id;
    ELSIF COALESCE(NEW.shikho_user_id, '') = '' AND COALESCE(NEW.shikho_account_key, '') <> '' THEN
        NEW.shikho_user_id := NEW.shikho_account_key;
    END IF;

    IF COALESCE(NEW.student_phone, '') = '' AND COALESCE(NEW.user_phone, '') <> '' THEN
        NEW.student_phone := NEW.user_phone;
    ELSIF COALESCE(NEW.user_phone, '') = '' AND COALESCE(NEW.student_phone, '') <> '' THEN
        NEW.user_phone := NEW.student_phone;
    END IF;

    IF COALESCE(NEW.student_class, '') = '' AND COALESCE(NEW.class_code, '') <> '' THEN
        NEW.student_class := NEW.class_code;
    ELSIF COALESCE(NEW.class_code, '') = '' AND COALESCE(NEW.student_class, '') <> '' THEN
        NEW.class_code := NEW.student_class;
    END IF;

    IF COALESCE(NEW.student_group, '') = '' AND COALESCE(NEW.group_code, '') <> '' THEN
        NEW.student_group := NEW.group_code;
    ELSIF COALESCE(NEW.group_code, '') = '' AND COALESCE(NEW.student_group, '') <> '' THEN
        NEW.group_code := NEW.student_group;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.installment_enabled IS DISTINCT FROM OLD.installment_enabled THEN
            NEW.allow_installments := NEW.installment_enabled;
        ELSIF NEW.allow_installments IS DISTINCT FROM OLD.allow_installments THEN
            NEW.installment_enabled := NEW.allow_installments;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_course_access_enrollment_columns ON public.course_access_enrollments;
CREATE TRIGGER trg_sync_course_access_enrollment_columns
BEFORE INSERT OR UPDATE ON public.course_access_enrollments
FOR EACH ROW EXECUTE FUNCTION public.sync_course_access_enrollment_columns();

GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;

NOTIFY pgrst, 'reload schema';

COMMIT;
