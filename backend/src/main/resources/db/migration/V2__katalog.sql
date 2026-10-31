-- Başlangıç kataloğu. TYT Matematik konu id'leri Android'deki SeedData ile aynı tutuldu.
-- Konu eklemek/düzeltmek için bu dosyayı değiştirme; yeni bir V3__... migration'ı yaz.

INSERT INTO exams (id, code, title, description, order_index) VALUES
    ('exam_kpss', 'KPSS', 'KPSS Genel Yetenek - Genel Kültür', 'Kamu Personeli Seçme Sınavı', 1),
    ('exam_tyt',  'TYT',  'TYT', 'Temel Yeterlilik Testi', 2),
    ('exam_ayt',  'AYT',  'AYT', 'Alan Yeterlilik Testi', 3);

INSERT INTO subjects (id, exam_id, name, order_index) VALUES
    ('kpss_turkce',      'exam_kpss', 'Türkçe', 1),
    ('kpss_matematik',   'exam_kpss', 'Matematik', 2),
    ('kpss_tarih',       'exam_kpss', 'Tarih', 3),
    ('kpss_cografya',    'exam_kpss', 'Coğrafya', 4),
    ('kpss_vatandaslik', 'exam_kpss', 'Vatandaşlık', 5),
    ('kpss_guncel',      'exam_kpss', 'Güncel Bilgiler', 6),
    ('tyt_matematik',    'exam_tyt',  'Matematik', 1),
    ('ayt_matematik',    'exam_ayt',  'Matematik', 1);

INSERT INTO topics (id, subject_id, name, order_index) VALUES
    ('kpss_tr_sozcuk',      'kpss_turkce', 'Sözcükte Anlam', 1),
    ('kpss_tr_cumle',       'kpss_turkce', 'Cümlede Anlam', 2),
    ('kpss_tr_paragraf',    'kpss_turkce', 'Paragraf', 3),
    ('kpss_tr_ses',         'kpss_turkce', 'Ses Bilgisi', 4),
    ('kpss_tr_yazim',       'kpss_turkce', 'Yazım ve Noktalama', 5),
    ('kpss_tr_dilbilgisi',  'kpss_turkce', 'Dil Bilgisi', 6),

    ('kpss_mat_temel',      'kpss_matematik', 'Temel Kavramlar ve Sayılar', 1),
    ('kpss_mat_problemler', 'kpss_matematik', 'Problemler', 2),
    ('kpss_mat_geometri',   'kpss_matematik', 'Geometri', 3),
    ('kpss_mat_mantik',     'kpss_matematik', 'Sayısal Mantık', 4),

    ('kpss_tar_oncesi',     'kpss_tarih', 'İslamiyet Öncesi Türk Tarihi', 1),
    ('kpss_tar_islam',      'kpss_tarih', 'İlk Türk-İslam Devletleri', 2),
    ('kpss_tar_osmanli',    'kpss_tarih', 'Osmanlı Tarihi', 3),
    ('kpss_tar_inkilap',    'kpss_tarih', 'İnkılap Tarihi ve Atatürkçülük', 4),
    ('kpss_tar_cagdas',     'kpss_tarih', 'Çağdaş Türk ve Dünya Tarihi', 5),

    ('kpss_cog_konum',      'kpss_cografya', 'Türkiye''nin Coğrafi Konumu', 1),
    ('kpss_cog_fiziki',     'kpss_cografya', 'Fiziki Coğrafya', 2),
    ('kpss_cog_iklim',      'kpss_cografya', 'İklim ve Bitki Örtüsü', 3),
    ('kpss_cog_nufus',      'kpss_cografya', 'Nüfus ve Yerleşme', 4),
    ('kpss_cog_ekonomi',    'kpss_cografya', 'Ekonomik Coğrafya', 5),

    ('kpss_vat_hukuk',      'kpss_vatandaslik', 'Temel Hukuk Kavramları', 1),
    ('kpss_vat_anayasa',    'kpss_vatandaslik', 'Anayasa Hukuku', 2),
    ('kpss_vat_idare',      'kpss_vatandaslik', 'İdare Hukuku', 3),

    ('kpss_gun_genel',      'kpss_guncel', 'Güncel Olaylar', 1),

    ('top_sayilar',         'tyt_matematik', 'Sayılar & Temel Kavramlar', 1),
    ('top_bolme',           'tyt_matematik', 'Bölme-Bölünebilme & EBOB-EKOK', 2),
    ('top_uslu_koklu',      'tyt_matematik', 'Üslü ve Köklü İfadeler', 3),
    ('top_problemler',      'tyt_matematik', 'Oran-Orantı & Problemler', 4),
    ('top_fonksiyonlar',    'tyt_matematik', 'Fonksiyonlar & Bileşke', 5),
    ('top_polinomlar',      'tyt_matematik', 'Polinomlar & Çarpanlara Ayırma', 6),
    ('top_olasilik',        'tyt_matematik', 'Permütasyon, Kombinasyon & Olasılık', 7);
