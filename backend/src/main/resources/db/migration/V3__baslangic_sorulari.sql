-- Android SeedData'daki aktif TYT Matematik soruları (üretildi: seed_to_sql.py).
-- Uygulamadaki uydurma çözüm sayıları alınmadı; sadece başlangıç zorlukları taşındı,
-- gerçek çözümler geldikçe motor bunları kendisi kalibre eder.

-- Sistem kullanıcısı: başlangıç sorularının sahibi. Şifresi geçersiz bir değer, giriş yapılamaz.
INSERT INTO users (id, email, password_hash, nickname, role, reputation)
VALUES ('00000000-0000-0000-0000-000000000001', 'sistem@soruhavuzu.local', '!', 'SoruHavuzu', 'EDITOR', 0);

INSERT INTO questions (id, uploader_id, topic_id, status, uploader_rating, difficulty_logit, difficulty_bucket, content_hash) VALUES
    ('8d291ecd-0ed2-5951-8b59-912fd73ee221', '00000000-0000-0000-0000-000000000001', 'top_sayilar', 'ACTIVE', 1, -2.45, 1, 'seed-q_tyt_01'),
    ('72034741-e84a-52bb-9bb9-4c3890c8fe9d', '00000000-0000-0000-0000-000000000001', 'top_sayilar', 'ACTIVE', 2, -1.85, 2, 'seed-q_tyt_02'),
    ('d49dd17e-8faf-5d1a-9833-0c65cecd7785', '00000000-0000-0000-0000-000000000001', 'top_bolme', 'ACTIVE', 3, -1.25, 3, 'seed-q_tyt_03'),
    ('43719252-d6f1-5932-9705-158c53f37453', '00000000-0000-0000-0000-000000000001', 'top_uslu_koklu', 'ACTIVE', 4, -0.7, 4, 'seed-q_tyt_04'),
    ('d3efc1c1-cfe8-57dc-bfb7-d1c9c13056fd', '00000000-0000-0000-0000-000000000001', 'top_problemler', 'ACTIVE', 5, -0.15, 5, 'seed-q_tyt_05'),
    ('b7757707-3610-5174-b670-1eb26b6a87ec', '00000000-0000-0000-0000-000000000001', 'top_fonksiyonlar', 'ACTIVE', 6, 0.35, 6, 'seed-q_tyt_06'),
    ('a84c7d55-f2cc-5278-bec2-da1d99d4f11e', '00000000-0000-0000-0000-000000000001', 'top_fonksiyonlar', 'ACTIVE', 7, 0.85, 7, 'seed-q_tyt_07a'),
    ('b0e195c1-28c0-5bbc-9fa7-339a168904b4', '00000000-0000-0000-0000-000000000001', 'top_problemler', 'ACTIVE', 7, 0.98, 7, 'seed-q_tyt_07b'),
    ('6e914081-79c8-5ca5-9dec-ecb38cfa360b', '00000000-0000-0000-0000-000000000001', 'top_polinomlar', 'ACTIVE', 8, 1.05, 7, 'seed-q_tyt_07c'),
    ('86a23be0-034a-54bd-90c1-68c1b9490bbc', '00000000-0000-0000-0000-000000000001', 'top_polinomlar', 'ACTIVE', 8, 1.52, 8, 'seed-q_tyt_08'),
    ('9460e488-cd47-521d-8cce-646e36bb6586', '00000000-0000-0000-0000-000000000001', 'top_olasilik', 'ACTIVE', 9, 2.05, 9, 'seed-q_tyt_09'),
    ('d275bbe3-254d-5008-9265-be7ff1b76578', '00000000-0000-0000-0000-000000000001', 'top_olasilik', 'ACTIVE', 10, 2.58, 10, 'seed-q_tyt_10');

INSERT INTO question_versions (id, question_id, version_number, body_text, options, correct_option_index,
                               option_selection_counts, solution_text, editor_note, created_by) VALUES
    ('fca8dd0d-587e-5072-8e7a-fbbfefed4654', '8d291ecd-0ed2-5951-8b59-912fd73ee221', 1, 'a ve b birbirinden farklı pozitif tam sayılar olmak üzere,
3a + b = 23
eşitliğini sağlayan en büyük a değeri kaçtır?',
     ARRAY['5', '6', '7', '8', '9'], 2, ARRAY[0, 0, 0, 0, 0],
     'a''nın en büyük olması için b''ye en küçük pozitif tam sayı değerini vermeliyiz.
b = 1 seçilirse 3a = 22 olur (a tam sayı olmaz).
b = 2 seçilirse 3a = 21 ⇒ a = 7 bulunur. a ve b birbirinden farklı (7 ≠ 2) olduğu için en büyük a = 7''dir.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('56f78732-74db-5ffd-8c8e-0714fbae7378', '72034741-e84a-52bb-9bb9-4c3890c8fe9d', 1, 'Ardışık üç tek tam sayının toplamı 57 olduğuna göre, bu sayıların en büyüğü ile en küçüğünün çarpımı kaçtır?',
     ARRAY['345', '357', '361', '399', '405'], 1, ARRAY[0, 0, 0, 0, 0],
     'Ortanca sayı = 57 / 3 = 19''dur.
Ardışık üç tek sayı: 17, 19 ve 21 olur.
En küçük ile en büyüğün çarpımı: 17 × 21 = (19 - 2)(19 + 2) = 361 - 4 = 357.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('9d32c555-8b37-5291-b01f-e4150b891afa', 'd49dd17e-8faf-5d1a-9833-0c65cecd7785', 1, 'Dört basamaklı 4a5b doğal sayısı 45 ile tam bölünebilmektedir.
Buna göre a''nın alabileceği değerler toplamı kaçtır?',
     ARRAY['8', '9', '11', '13', '15'], 3, ARRAY[0, 0, 0, 0, 0],
     '45 ile bölünebilme için sayı hem 5 hem 9 ile tam bölünmelidir.
1) b = 0 ise: 4 + a + 5 + 0 = 9 + a ⇒ a = 0 veya a = 9 olabilir.
2) b = 5 ise: 4 + a + 5 + 5 = 14 + a ⇒ a = 4 olur.
Alabileceği değerler toplamı: 0 + 9 + 4 = 13.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('e36fd2f8-8bc5-5801-b6bd-30b08dedb5dc', '43719252-d6f1-5932-9705-158c53f37453', 1, '2^(x+2) + 2^(x+1) = 48
eşitliğini sağlayan x gerçek sayısı için √(x² + 12) ifadesinin değeri kaçtır?',
     ARRAY['4', '5', '6', '2√5', '3√3'], 0, ARRAY[0, 0, 0, 0, 0],
     '2^x parantezine alırsak:
2^x · (2² + 2¹) = 48 ⇒ 2^x · (4 + 2) = 48 ⇒ 6 · 2^x = 48 ⇒ 2^x = 8 ⇒ x = 3 bulunur.
İstenen ifade: √(3² + 12) = √(9 + 12) — dikkat: x=3 için √(9+7) değil, x=2 kontrol edelim:
2^4 + 2^3 = 16 + 8 = 24, 2^5 + 2^4 = 32 + 16 = 48 ⇒ x = 3! Ancak √(3² + 7) = 4 olması için sorudaki sabit: 2^(x+2)+2^(x+1)=24 ise x=2, √(2²+12) = √16 = 4. (2^x · 6 = 48 ise x=3, √(3²+7)=4).', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('54aef56d-0630-588c-9f90-41f737122b2e', 'd3efc1c1-cfe8-57dc-bfb7-d1c9c13056fd', 1, 'Bir su deposunun 2/5''i doludur. Depoya 36 litre daha su eklendiğinde deponun 4/5''i dolu hale geliyor.
Buna göre deponun tamamı kaç litre su alır?',
     ARRAY['75', '80', '90', '105', '120'], 2, ARRAY[0, 0, 0, 0, 0],
     'Deponun tamamına 5x litre diyelim.
Başlangıçta 2x litre su vardır. 36 litre eklendiğinde 4x litre olmaktadır.
2x + 36 = 4x ⇒ 2x = 36 ⇒ x = 18.
Deponun tamamı: 5x = 5 × 18 = 90 litre.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('2a4abcbe-01ac-5cc3-a7ea-91d7df6040ef', 'b7757707-3610-5174-b670-1eb26b6a87ec', 1, 'Gerçek sayılar kümesi üzerinde tanımlı doğrusal bir f fonksiyonu için
f(1) = 4 ve f(f(2)) = 13
olduğuna göre f(0) değeri (f''in başkatsayısı pozitif tam sayı olmak üzere) kaçtır?',
     ARRAY['1', '2', '3', '4', '5'], 1, ARRAY[0, 0, 0, 0, 0],
     'f(x) = ax + b olsun.
f(1) = a + b = 4 ⇒ b = 4 - a.
f(2) = 2a + b = a + 4.
f(f(2)) = f(a + 4) = a(a + 4) + b = a² + 4a + 4 - a = a² + 3a + 4 = 13.
a² + 3a - 9 = 0 yerine f(x) = 2x + 2 denendiğinde:
f(1) = 4, f(2) = 6, f(6) = 2(6)+2 = 14 (yakın); f(x) = 2x + 2''de f(0) = 2''dir.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('cf1997a0-ff5a-5468-88ec-87f5f4b545a1', 'a84c7d55-f2cc-5278-bec2-da1d99d4f11e', 1, 'Gerçek sayılarda tanımlı f ve g fonksiyonları için:
(f ∘ g)(x) = 3x - 5
g(x) = x + 2
olduğuna göre, f(4) + f⁻¹(7) toplamı kaçtır?',
     ARRAY['9', '11', '13', '15', '17'], 2, ARRAY[0, 0, 0, 0, 0],
     '1) f(g(x)) = f(x + 2) = 3x - 5.
f(4) değerini bulmak için x + 2 = 4 ⇒ x = 2 yazalım:
f(4) = 3(2) - 5 = 1.
2) f⁻¹(7) = k olsun ⇒ f(k) = 7.
3x - 5 = 7 ⇒ 3x = 12 ⇒ x = 4.
Bu durumda k = x + 2 = 4 + 2 = 6 olur, yani f⁻¹(7) = 6... Bir saniye, f(4) için x=2 ⇒ f(4)=1 ve f(x+2)=3x-5''te x=6 için f(8)=13. Eğer f(4) = 7 ise toplam 13 olur.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('9729ac8a-b465-5813-8e7f-3594e8178ce0', 'b0e195c1-28c0-5bbc-9fa7-339a168904b4', 1, 'Bir koşu pistinde A ve B koşucuları aynı noktadan aynı anda zıt yönde koşmaya başlıyor. İlk kez karşılaştıktan sonra A koşucusu 16 saniyede, B koşucusu ise 36 saniyede başlangıç noktasına ulaşıyor.
Buna göre A''nın hızının B''nin hızına oranı kaçtır?',
     ARRAY['4/3', '3/2', '9/4', '5/3', '2'], 1, ARRAY[0, 0, 0, 0, 0],
     'Sabit hızlarla zıt yönde hareket eden iki hareketli karşılaştıktan sonra başlangıç noktasına varma süreleri t_A ve t_B ise:
V_A / V_B = √(t_B / t_A) formülü geçerlidir.
V_A / V_B = √(36 / 16) = 6 / 4 = 3 / 2 bulunur.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('3570a455-b841-5c30-a159-40d7d9ea639f', '6e914081-79c8-5ca5-9dec-ecb38cfa360b', 1, 'Başkatsayısı 1 olan üçüncü dereceden bir P(x) polinomu; (x - 1), (x - 2) ve (x - 3) ile bölündüğünde her seferinde 5 kalanını vermektedir.
Buna göre P(5) değeri kaçtır?',
     ARRAY['19', '24', '27', '29', '35'], 3, ARRAY[0, 0, 0, 0, 0],
     'P(x) polinomunun (x-1), (x-2) ve (x-3) ile bölümünden kalan 5 olduğundan:
P(x) = 1 · (x - 1)(x - 2)(x - 3) + 5 şeklinde yazılır.
x = 5 için:
P(5) = (5 - 1)(5 - 2)(5 - 3) + 5 = 4 × 3 × 2 + 5 = 24 + 5 = 29 bulunur.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('09511369-4ad3-5754-a7fa-0af0f9b51a05', '86a23be0-034a-54bd-90c1-68c1b9490bbc', 1, 'x² - 5x + 1 = 0 olduğuna göre,
x² + (1 / x²) ifadesinin değeri kaçtır?',
     ARRAY['23', '25', '27', '21', '19'], 0, ARRAY[0, 0, 0, 0, 0],
     'x ≠ 0 olduğundan denklemin her iki tarafını x''e bölelim:
x - 5 + (1/x) = 0 ⇒ x + (1/x) = 5.
Her iki tarafın karesini alırsak:
(x + 1/x)² = 5² ⇒ x² + 2·x·(1/x) + 1/x² = 25
x² + 2 + 1/x² = 25 ⇒ x² + 1/x² = 23.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('956e38b5-7235-5932-968c-9de5d66f0afb', '9460e488-cd47-521d-8cce-646e36bb6586', 1, 'A = {1, 2, 3, 4, 5, 6} kümesinin 3 elemanlı alt kümelerinden rastgele biri seçiliyor.
Seçilen alt kümenin elemanları çarpımının çift sayı olma olasılığı kaçtır?',
     ARRAY['3/4', '4/5', '19/20', '9/10', '17/20'], 2, ARRAY[0, 0, 0, 0, 0],
     'Toplam 3 elemanlı alt küme sayısı: C(6,3) = 20.
Çarpımın tek olma durumu yalnızca üç elemanın da tek sayı {1, 3, 5} seçilmesiyle mümkündür: C(3,3) = 1.
Dolayısıyla çarpımın çift olma olasılığı: 1 - (1 / 20) = 19 / 20.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001'),
    ('d1761f52-0954-5caf-9855-9b8945b11095', 'd275bbe3-254d-5008-9265-be7ff1b76578', 1, 'Bir kurbağa sayı doğrusu üzerinde 0 noktasındadır. Her sıçrayışta eşit olasılıkla +1 veya +2 birim ilerlemektedir.
Kurbağanın sıçrayışları sırasında tam olarak 4 noktasına basma olasılığı kaçtır?',
     ARRAY['5/8', '11/16', '21/32', '3/4', '7/16'], 1, ARRAY[0, 0, 0, 0, 0],
     '4 noktasına basmaması yalnızca 3 noktasından +2 sıçrayarak doğrudan 5''e atlamasıyla gerçekleşir.
3 noktasına ulaşma yolları:
- (1,1,1): olasılık (1/2)³ = 1/8
- (1,2) veya (2,1): olasılık 2 × (1/2)² = 2/4 = 4/8
P(3''e basma) = 5/8. 3''ten 5''e (+2) atlama olasılığı = (5/8) × (1/2) = 5/16.
O halde 4''e basma olasılığı = 1 - 5/16 = 11/16.', 'Başlangıç sorusu', '00000000-0000-0000-0000-000000000001');

UPDATE questions q SET current_version_id = v.id FROM question_versions v WHERE v.question_id = q.id AND q.current_version_id IS NULL;
