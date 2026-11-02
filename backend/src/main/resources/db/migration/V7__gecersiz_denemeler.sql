-- Cevap anahtarı düzeltilen sorularda eski anahtarla yapılan ilk denemeler geçersiz sayılır:
-- seviye (θ) etkileri kullanıcılardan geri alınır ve istatistiklerde sayılmazlar.
ALTER TABLE attempts ADD COLUMN voided BOOLEAN NOT NULL DEFAULT false;
