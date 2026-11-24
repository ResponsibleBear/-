import { useState } from 'react';
import { Download } from 'lucide-react';

interface DownloadAndroidZipButtonProps {
  className?: string;
}

export function DownloadAndroidZipButton({ className = '' }: DownloadAndroidZipButtonProps) {
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDownload = async () => {
    setDownloading(true);
    setError(null);
    try {
      const res = await fetch('/api/android-project.zip');
      if (!res.ok) {
        throw new Error('Не удалось сформировать архив');
      }
      const blob = await res.blob();
      const blobUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = 'alice-dom-android.zip';
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 3000);
    } catch {
      setError('Браузер заблокировал загрузку из окна предпросмотра. Откройте превью в новой вкладке или скачайте проект через кнопку Download в верхнем меню AI Studio (папка android-app).');
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="space-y-1.5">
      <button
        type="button"
        onClick={handleDownload}
        disabled={downloading}
        className={`btn-3d-primary min-h-[48px] px-4 py-2.5 rounded-[18px] disabled:opacity-60 text-white text-xs font-bold flex items-center justify-center gap-2 transition-all duration-120 w-full cursor-pointer ${className}`}
      >
        <Download className={`w-4 h-4 shrink-0 ${downloading ? 'animate-bounce' : ''}`} />
        <span>{downloading ? 'Подготовка архива...' : 'Скачать ZIP для Android Studio'}</span>
      </button>
      {error && (
        <p className="text-[11px] text-amber-600 dark:text-amber-300 leading-snug">{error}</p>
      )}
    </div>
  );
}
