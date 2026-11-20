-- =============================================================================
-- MAXBIRD FINAL PRODUCTION ZERO-TRUST SECURITY SQL (100% LIVE-DB MATCHED)
-- Admin User: maiafahim1234@gmail.com (0ac2cb61-e574-49a3-b9aa-80fd1781c396)
--
-- এই SQL-টি আপনার লাইভ ডাটাবেজের আসল ১৪টি টেবিল, ৩টি গার্ড ট্রিগার এবং
-- ৪০টি ফাংশনের সাথে ১০০% মিলিয়ে তৈরি করা হয়েছে:
--   1. `admin_users` + `is_admin_user()` / `is_admin()` / `check_is_admin()` তৈরি।
--   2. `app_security_config` (Mod APK SHA-256 সিগনেচার ব্লকার) তৈরি।
--   3. ৩টি `fn_guard_*_direct_tamper` ট্রিগার আপগ্রেড — যাতে লগইন করা Admin
--      (`authenticated` + `is_admin_user()`) কখনো ব্লক না হয়, কিন্তু হ্যাকার/anon ব্লক থাকে!
--   4. `request_student_verification` আপগ্রেড — আপনার ভার্সন-ব্লক লজিক ১০০% অক্ষুণ্ণ
--      রেখে `allowed_release_signatures` (Mod APK চেক) ও ban-protection যুক্ত।
--   5. `ai_agent_configs` (GitHub PAT, Telegram Session/2FA), `ai_agent_tasks`,
--      `ai_agent_schedules`, `admin_push_devices`, `devices`, `device_shikho_accounts`,
--      `course_access_enrollments`, `course_payment_transactions`, `onesignal_config`
--      সবগুলো `anon`-এর জন্য সম্পূর্ণ লক + কেবল Admin (`authenticated`) ও `service_role`-এর জন্য উন্মুক্ত।
--   6. ১৩টি `admin_*` / সংবেদনশীল RPC ফাংশন থেকে `anon`-এর EXECUTE পারমিশন বাতিল।
-- =============================================================================

BEGIN;

-- ১. অ্যাডমিন ইউজার টেবিল ও আপনার অ্যাডমিন অ্যাকাউন্ট যুক্ত করা
CREATE TABLE IF NOT EXISTS public.admin_users (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email text NOT NULL,
    role text NOT NULL DEFAULT 'super_admin',
    created_at timestamp with time zone DEFAULT now()
);

INSERT INTO public.admin_users (user_id, email, role)
VALUES ('0ac2cb61-e574-49a3-b9aa-80fd1781c396', 'maiafahim1234@gmail.com', 'super_admin')
ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email;

-- ২. অ্যাডমিন যাচাই ফাংশন (Admin App-এর primary ও fallback উভয়ের সাথে ১০০% মিল)
CREATE OR REPLACE FUNCTION public.is_admin_user()
RETURNS BOOLEAN LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.admin_users WHERE user_id = auth.uid()
    );
$$;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public AS $$
    SELECT public.is_admin_user();
$$;

CREATE OR REPLACE FUNCTION public.check_is_admin()
RETURNS BOOLEAN LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public AS $$
    SELECT public.is_admin_user();
$$;

-- ৩. সার্ভার-সাইড Mod APK সিগনেচার ব্লকার কনফিগ টেবিল (Admin App Settings কার্ডের সাথে সিঙ্কড)
CREATE TABLE IF NOT EXISTS public.app_security_config (
    id integer NOT NULL DEFAULT 1 CHECK (id = 1),
    allowed_release_signatures text[] NOT NULL DEFAULT '{}'::text[],
    max_admin_push_per_minute integer NOT NULL DEFAULT 20,
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT app_security_config_pkey PRIMARY KEY (id)
);

INSERT INTO public.app_security_config (id, allowed_release_signatures)
VALUES (1, '{}'::text[])
ON CONFLICT (id) DO NOTHING;

-- ৪. ৩টি Anti-Tamper Guard Trigger আপগ্রেড
-- (আগে `current_user IN ('anon', 'authenticated')` থাকায় লগইন করা Admin App-ও ব্লক হয়ে যেত!
-- এখন কেবল `anon` এবং নন-অ্যাডমিন ইউজার ব্লক হবে, কিন্তু ভেরিফাইড Admin নির্বিঘ্নে কাজ করতে পারবে।)
CREATE OR REPLACE FUNCTION public.fn_guard_devices_direct_tamper()
RETURNS trigger LANGUAGE plpgsql AS $function$
BEGIN
    IF current_user = 'anon' OR (current_user = 'authenticated' AND NOT public.is_admin_user()) THEN
        IF TG_OP = 'INSERT' THEN
            NEW.status := 'pending';
            NEW.is_banned := false;
            NEW.is_blocked := false;
            NEW.can_view_profiles := false;
        ELSIF TG_OP = 'UPDATE' THEN
            NEW.is_banned := OLD.is_banned;
            NEW.is_blocked := OLD.is_blocked;
            NEW.ban_reason := OLD.ban_reason;
            NEW.rejection_reason := OLD.rejection_reason;
            NEW.can_view_profiles := OLD.can_view_profiles;
            NEW.is_access_restricted := OLD.is_access_restricted;
            NEW.allowed_classes := OLD.allowed_classes;
            NEW.allowed_groups := OLD.allowed_groups;
            NEW.allowed_batches := OLD.allowed_batches;
            NEW.allowed_programs := OLD.allowed_programs;
            IF LOWER(COALESCE(NEW.status, '')) IN ('active', 'approved')
               AND LOWER(COALESCE(OLD.status, '')) NOT IN ('active', 'approved') THEN
                NEW.status := OLD.status;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.fn_guard_enrollments_direct_tamper()
RETURNS trigger LANGUAGE plpgsql AS $function$
BEGIN
    IF current_user = 'anon' OR (current_user = 'authenticated' AND NOT public.is_admin_user()) THEN
        IF TG_OP = 'INSERT' THEN
            NEW.status := 'REQUESTED';
            NEW.access_status := 'REQUESTED';
            NEW.paid_amount := 0;
            NEW.approved_at := NULL;
            NEW.unlocked_at := NULL;
        ELSIF TG_OP = 'UPDATE' THEN
            NEW.paid_amount := OLD.paid_amount;
            NEW.total_price := OLD.total_price;
            NEW.total_fee := OLD.total_fee;
            NEW.approved_at := OLD.approved_at;
            NEW.unlocked_at := OLD.unlocked_at;
            NEW.trial_days := OLD.trial_days;
            NEW.trial_hours := OLD.trial_hours;
            IF UPPER(COALESCE(NEW.status, '')) = 'ACTIVE'
               AND UPPER(COALESCE(OLD.status, '')) <> 'ACTIVE' THEN
                NEW.status := OLD.status;
                NEW.access_status := OLD.access_status;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.fn_guard_payment_tx_direct_tamper()
RETURNS trigger LANGUAGE plpgsql AS $function$
BEGIN
    IF current_user = 'anon' OR (current_user = 'authenticated' AND NOT public.is_admin_user()) THEN
        NEW.status := 'PENDING';
        NEW.reviewed_at := NULL;
    END IF;
    RETURN NEW;
END;
$function$;

-- ৫. request_student_verification আপগ্রেড
-- (আপনার আগের ভার্সন-ব্লক লজিক ১০০% ঠিক রেখে + Mod APK signature চেক + Ban-overwrite প্রটেকশন যুক্ত)
CREATE OR REPLACE FUNCTION public.request_student_verification(
    p_device_hash text,
    p_display_device_id text DEFAULT ''::text,
    p_device_model text DEFAULT ''::text,
    p_device_brand text DEFAULT ''::text,
    p_android_version text DEFAULT ''::text,
    p_app_version text DEFAULT ''::text,
    p_student_note text DEFAULT ''::text,
    p_app_signature text DEFAULT ''::text
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $function$
DECLARE
    v_existing record;
    v_allowed_sigs text[];
    v_clean_hash text := left(btrim(COALESCE(p_device_hash, '')), 128);
    v_clean_sig text := lower(btrim(COALESCE(p_app_signature, '')));
    v_note text := left(btrim(COALESCE(p_student_note, '')), 80);
    v_req_ver text := public.get_required_app_version_name();
    v_client_ver text := NULLIF(BTRIM(REGEXP_REPLACE(COALESCE(p_app_version, ''), '^[vV]', '')), '');
BEGIN
    IF v_clean_hash = '' OR length(v_clean_hash) < 8 THEN
        RETURN jsonb_build_object('success', false, 'status', 'error', 'is_banned', false, 'message', 'অবৈধ ডিভাইস আইডি।');
    END IF;

    -- ১. বাধ্যতামূলক অ্যাপ ভার্সন চেক (আপনার আগের লজিক অক্ষুণ্ণ)
    IF v_req_ver <> '' AND v_client_ver IS NOT NULL AND LOWER(v_client_ver) <> LOWER(v_req_ver) THEN
        RETURN jsonb_build_object(
            'success', false,
            'status', 'blocked',
            'is_banned', true,
            'ban_reason', 'পুরাতন ভার্সনের অ্যাপ (v' || v_client_ver || ') ব্যবহার করা যাবে না! শুধুমাত্র নির্ধারিত ভার্সন (v' || v_req_ver || ') ইনস্টল করুন।'
        );
    END IF;

    -- ২. সার্ভার-সাইড Mod APK সিগনেচার চেক (যদি Admin Settings-এ allowed_release_signatures সেট করা থাকে)
    SELECT allowed_release_signatures INTO v_allowed_sigs
    FROM public.app_security_config
    WHERE id = 1;

    IF v_allowed_sigs IS NOT NULL AND array_length(v_allowed_sigs, 1) > 0 THEN
        IF v_clean_sig = '' OR NOT EXISTS (
            SELECT 1 FROM unnest(v_allowed_sigs) AS s
            WHERE lower(btrim(s)) = v_clean_sig
        ) THEN
            RETURN jsonb_build_object(
                'success', false,
                'status', 'blocked',
                'is_banned', true,
                'ban_reason', 'অননুমোদিত বা পরিবর্তিত (Modded) APK শনাক্ত হয়েছে। অনুগ্রহ করে অফিশিয়াল অ্যাপ ব্যবহার করুন।'
            );
        END IF;
    END IF;

    SELECT * INTO v_existing FROM public.devices WHERE device_hash = v_clean_hash LIMIT 1;

    IF v_existing.device_hash IS NOT NULL THEN
        IF COALESCE(v_existing.is_banned, false) = true
           OR COALESCE(v_existing.is_blocked, false) = true
           OR lower(COALESCE(v_existing.status, '')) IN ('banned', 'blocked', 'rejected', 'revoked', 'suspended') THEN
            RETURN jsonb_build_object(
                'success', false,
                'status', 'blocked',
                'is_banned', true,
                'ban_reason', COALESCE(NULLIF(btrim(v_existing.ban_reason), ''), NULLIF(btrim(v_existing.rejection_reason), ''), 'এই ডিভাইসে অ্যাপ ব্যবহারের অনুমতি স্থগিত করা হয়েছে।')
            );
        END IF;

        IF lower(COALESCE(v_existing.status, '')) IN ('active', 'approved') THEN
            RETURN jsonb_build_object(
                'success', true,
                'status', 'active',
                'is_banned', false,
                'can_view_profiles', COALESCE(v_existing.can_view_profiles, false),
                'session_token', COALESCE(NULLIF(btrim(v_existing.session_token), ''), 'VERIFIED-' || v_clean_hash)
            );
        END IF;
    END IF;

    INSERT INTO public.devices (
        device_hash, display_device_id, device_model, device_brand,
        android_version, app_version, student_note, applied_code,
        status, is_banned, requested_at, updated_at
    ) VALUES (
        v_clean_hash,
        left(COALESCE(p_display_device_id, ''), 64),
        left(COALESCE(p_device_model, ''), 80),
        left(COALESCE(p_device_brand, ''), 40),
        left(COALESCE(p_android_version, ''), 64),
        left(COALESCE(p_app_version, ''), 32),
        v_note,
        CASE WHEN v_note <> '' THEN 'REQ: ' || v_note ELSE 'STUDENT-VERIFY' END,
        'pending', false, now(), now()
    )
    ON CONFLICT (device_hash) DO UPDATE SET
        display_device_id = CASE WHEN EXCLUDED.display_device_id <> '' THEN EXCLUDED.display_device_id ELSE public.devices.display_device_id END,
        device_model = COALESCE(NULLIF(EXCLUDED.device_model, ''), public.devices.device_model),
        device_brand = COALESCE(NULLIF(EXCLUDED.device_brand, ''), public.devices.device_brand),
        android_version = COALESCE(NULLIF(EXCLUDED.android_version, ''), public.devices.android_version),
        app_version = COALESCE(NULLIF(EXCLUDED.app_version, ''), public.devices.app_version),
        student_note = CASE WHEN v_note <> '' THEN v_note ELSE public.devices.student_note END,
        applied_code = CASE WHEN v_note <> '' THEN 'REQ: ' || v_note ELSE COALESCE(NULLIF(public.devices.applied_code, ''), 'STUDENT-VERIFY') END,
        status = CASE
            WHEN COALESCE(public.devices.is_banned, false) = true OR COALESCE(public.devices.is_blocked, false) = true THEN public.devices.status
            WHEN lower(COALESCE(public.devices.status, '')) IN ('active', 'approved') THEN 'active'
            ELSE 'pending'
        END,
        requested_at = now(),
        updated_at = now();

    RETURN jsonb_build_object(
        'success', true,
        'status', 'pending',
        'is_banned', false,
        'student_note', v_note,
        'message', 'স্বাগতম! আপনার স্টুডেন্ট ভেরিফিকেশন আবেদন সফলভাবে গৃহীত হয়েছে।'
    );
END;
$function$;

-- ৬. আপনার লাইভ ডাটাবেজের সব ১৬টি টেবিলে RLS এনাবল ও পুরনো অনিরাপদ পলিসি ক্লিন করা
DO $$
DECLARE
    tbl TEXT;
    pol RECORD;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'admin_users',
        'app_security_config',
        'onesignal_config',
        'admin_push_devices',
        'app_updates',
        'app_notices',
        'activation_support_links',
        'course_payment_settings',
        'student_notifications',
        'devices',
        'device_shikho_accounts',
        'course_access_enrollments',
        'course_payment_transactions',
        'ai_agent_configs',
        'ai_agent_tasks',
        'ai_agent_schedules'
    ] LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = tbl
        ) THEN
            EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
            FOR pol IN (
                SELECT policyname FROM pg_policies
                WHERE schemaname = 'public' AND tablename = tbl
            ) LOOP
                EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, tbl);
            END LOOP;
        END IF;
    END LOOP;
END $$;

-- ৭. Zero-Trust RLS পলিসি সেটআপ (আসল ১৪টি টেবিল + ২টি নতুন সিকিউরিটি টেবিল)

-- ৭.১ admin_users: অ্যাডমিন শুধু নিজের রো পড়তে পারবে
CREATE POLICY "Admin read own admin_users" ON public.admin_users
    FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY "Service role full admin_users" ON public.admin_users
    FOR ALL TO service_role USING (true) WITH CHECK (true);

-- ৭.২ পাবলিক রিড-অনলি টেবিল (Student App `anon` শুধু active ডেটা পড়বে; Admin `authenticated` সব পারবে)
CREATE POLICY "Anon read active app_updates" ON public.app_updates
    FOR SELECT TO anon USING (is_active = true);
CREATE POLICY "Admin full app_updates" ON public.app_updates
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full app_updates" ON public.app_updates
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Anon read active app_notices" ON public.app_notices
    FOR SELECT TO anon USING (is_active = true);
CREATE POLICY "Admin full app_notices" ON public.app_notices
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full app_notices" ON public.app_notices
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Anon read active support_links" ON public.activation_support_links
    FOR SELECT TO anon USING (is_active = true);
CREATE POLICY "Admin full support_links" ON public.activation_support_links
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full support_links" ON public.activation_support_links
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Anon read payment_settings" ON public.course_payment_settings
    FOR SELECT TO anon USING (true);
CREATE POLICY "Admin full payment_settings" ON public.course_payment_settings
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full payment_settings" ON public.course_payment_settings
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Anon read active student_notifications" ON public.student_notifications
    FOR SELECT TO anon USING (is_active = true);
CREATE POLICY "Admin full student_notifications" ON public.student_notifications
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full student_notifications" ON public.student_notifications
    FOR ALL TO service_role USING (true) WITH CHECK (true);

-- ৭.৩ সংবেদনশীল টেবিল: `anon`-এর সরাসরি অ্যাক্সেস সম্পূর্ণ বন্ধ!
-- (Student App নিরাপদ SECURITY DEFINER RPC ব্যবহার করবে; Admin App লগইন করে সরাসরি কাজ করবে)
CREATE POLICY "Admin full app_security_config" ON public.app_security_config
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full app_security_config" ON public.app_security_config
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full onesignal_config" ON public.onesignal_config
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full onesignal_config" ON public.onesignal_config
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full admin_push_devices" ON public.admin_push_devices
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full admin_push_devices" ON public.admin_push_devices
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full devices" ON public.devices
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full devices" ON public.devices
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full device_shikho_accounts" ON public.device_shikho_accounts
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full device_shikho_accounts" ON public.device_shikho_accounts
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full course_access_enrollments" ON public.course_access_enrollments
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full course_access_enrollments" ON public.course_access_enrollments
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full course_payment_transactions" ON public.course_payment_transactions
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full course_payment_transactions" ON public.course_payment_transactions
    FOR ALL TO service_role USING (true) WITH CHECK (true);

-- ৭.৪ গোপন GitHub PAT ও Telegram Session/2FA টেবিল লকডাউন (আগে পাবলিক ছিল!)
CREATE POLICY "Admin full ai_agent_configs" ON public.ai_agent_configs
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full ai_agent_configs" ON public.ai_agent_configs
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full ai_agent_tasks" ON public.ai_agent_tasks
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full ai_agent_tasks" ON public.ai_agent_tasks
    FOR ALL TO service_role USING (true) WITH CHECK (true);

CREATE POLICY "Admin full ai_agent_schedules" ON public.ai_agent_schedules
    FOR ALL TO authenticated USING (public.is_admin_user()) WITH CHECK (public.is_admin_user());
CREATE POLICY "Service role full ai_agent_schedules" ON public.ai_agent_schedules
    FOR ALL TO service_role USING (true) WITH CHECK (true);

-- ৮. Table-level GRANT লকডাউন
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon;
GRANT SELECT ON public.app_updates,
                public.app_notices,
                public.activation_support_links,
                public.course_payment_settings,
                public.student_notifications
TO anon;

GRANT ALL ON ALL TABLES IN SCHEMA public TO authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO authenticated, service_role;

-- ৯. RPC Function EXECUTE পারমিশন লকডাউন (১৩টি admin_* ও সংবেদনশীল ফাংশন থেকে anon ব্লক!)
DO $$
DECLARE
    fn RECORD;
BEGIN
    -- সব অ্যাডমিন ফাংশন থেকে PUBLIC ও anon-এর EXECUTE বাতিল করে কেবল authenticated ও service_role-কে দেওয়া
    FOR fn IN (
        SELECT p.oid::regprocedure AS sig
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND (
              p.proname LIKE 'admin_%'
              OR p.proname IN (
                  'notify_student_via_onesignal',
                  'update_onesignal_config_secure',
                  'is_admin_user',
                  'is_admin',
                  'check_is_admin'
              )
          )
    ) LOOP
        EXECUTE 'REVOKE EXECUTE ON FUNCTION ' || fn.sig || ' FROM PUBLIC, anon';
        EXECUTE 'GRANT EXECUTE ON FUNCTION ' || fn.sig || ' TO authenticated, service_role';
    END LOOP;

    -- Student App-এর প্রয়োজনীয় নিরাপদ RPC ফাংশনগুলোতে anon + authenticated চালু রাখা
    FOR fn IN (
        SELECT p.oid::regprocedure AS sig
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.proname IN (
              'check_student_verification_status',
              'request_student_verification',
              'verify_device_heartbeat',
              'check_device_status',
              'get_onesignal_public_app_id',
              'sync_student_push_identity',
              'notify_admin_via_onesignal',
              'get_student_course_commerce_status',
              'request_course_purchase_access',
              'submit_course_payment_transaction',
              'start_course_free_trial',
              'get_required_app_version_name',
              'is_global_kill_switch_active',
              'enforce_overdue_course_installments'
          )
    ) LOOP
        EXECUTE 'GRANT EXECUTE ON FUNCTION ' || fn.sig || ' TO anon, authenticated, service_role';
    END LOOP;
END $$;

NOTIFY pgrst, 'reload schema';

COMMIT;
