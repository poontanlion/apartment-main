import React, { useState, useEffect } from 'react';
import { X, FileText, Upload, ExternalLink, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

interface ScannedDocumentModalProps {
  title: string;
  subtitle?: string;
  documentUrl?: string;
  onClose: () => void;
  onSave?: (newDocUrl: string) => Promise<void> | void;
  readOnly?: boolean;
}

export const ScannedDocumentModal: React.FC<ScannedDocumentModalProps> = ({
  title,
  subtitle,
  documentUrl,
  onClose,
  onSave,
  readOnly = false,
}) => {
  const [currentDoc, setCurrentDoc] = useState<string>(documentUrl || '');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    setCurrentDoc(documentUrl || '');
  }, [documentUrl]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 14 * 1024 * 1024) {
      toast.error('ขนาดไฟล์ต้องไม่เกิน 14MB');
      return;
    }

    const reader = new FileReader();
    reader.onload = async (event) => {
      const base64Data = event.target?.result as string;
      if (onSave) {
        setIsSaving(true);
        try {
          await onSave(base64Data);
          setCurrentDoc(base64Data);
          toast.success('อัปโหลดและบันทึกเอกสารสแกนเรียบร้อยแล้ว');
        } catch {
          toast.error('ไม่สามารถบันทึกเอกสารได้');
        } finally {
          setIsSaving(false);
        }
      } else {
        setCurrentDoc(base64Data);
      }
    };
    reader.onerror = () => toast.error('ไม่สามารถอ่านไฟล์ได้');
    reader.readAsDataURL(file);
  };

  const handleRemoveDoc = async () => {
    if (!confirm('ต้องการลบไฟล์เอกสารสแกนนี้ใช่หรือไม่?')) return;
    if (onSave) {
      setIsSaving(true);
      try {
        await onSave('');
        setCurrentDoc('');
        toast.success('ลบเอกสารสแกนเรียบร้อยแล้ว');
      } catch {
        toast.error('เกิดข้อผิดพลาดในการลบเอกสาร');
      } finally {
        setIsSaving(false);
      }
    } else {
      setCurrentDoc('');
    }
  };

  const isPdf = currentDoc?.startsWith('data:application/pdf') || currentDoc?.toLowerCase().endsWith('.pdf');

  return (
    <div
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto"
    >
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl max-w-3xl w-full p-6 shadow-2xl space-y-5 my-6 text-slate-900 dark:text-white">

        {/* HEADER */}
        <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-blue-600/10 text-blue-600 rounded-xl flex items-center justify-center font-bold">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold leading-tight">{title}</h2>
              {subtitle && <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{subtitle}</p>}
            </div>
          </div>

          <div className="flex items-center gap-2">
            {currentDoc && (
              <a
                href={currentDoc}
                target="_blank"
                rel="noreferrer"
                className="px-3 py-1.5 text-xs font-semibold rounded-xl bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 transition-colors flex items-center gap-1.5 cursor-pointer"
                title="เปิดในแท็บใหม่"
              >
                <ExternalLink className="w-3.5 h-3.5" /> เปิดแท็บใหม่
              </a>
            )}
            <button
              onClick={onClose}
              className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition-colors cursor-pointer"
              title="ปิด"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* DOCUMENT PREVIEW CONTAINER */}
        <div className="min-h-[300px] max-h-[65vh] flex items-center justify-center bg-slate-50 dark:bg-slate-950/60 rounded-2xl border border-dashed border-slate-300 dark:border-slate-700 p-4 overflow-hidden relative">
          {currentDoc ? (
            isPdf ? (
              <iframe
                src={currentDoc}
                title="Scanned Document PDF"
                className="w-full h-[60vh] rounded-xl border border-slate-200 dark:border-slate-800"
              />
            ) : (
              <img
                src={currentDoc}
                alt="Scanned Document"
                className="max-h-[60vh] w-auto max-w-full object-contain rounded-xl shadow-xs"
              />
            )
          ) : (
            <div className="text-center py-12 px-4 space-y-3">
              <div className="w-14 h-14 mx-auto rounded-2xl bg-slate-200 dark:bg-slate-800 flex items-center justify-center text-slate-400">
                <FileText className="w-7 h-7" />
              </div>
              <div>
                <h3 className="font-semibold text-sm">ยังไม่มีเอกสารสแกน</h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  สามารถอัปโหลดไฟล์สแกน (รูปภาพ PNG, JPG หรือไฟล์ PDF) เข้าสู่ระบบได้ทันที
                </p>
              </div>
            </div>
          )}
        </div>


        {/* BOTTOM ACTION BAR */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2 border-t border-slate-200 dark:border-slate-800">
          <div className="flex items-center gap-2 w-full sm:w-auto">
            {!readOnly && onSave && (
              <label className="px-4 py-2 text-xs font-semibold rounded-xl bg-blue-600 hover:bg-blue-700 text-white transition-colors flex items-center justify-center gap-1.5 cursor-pointer shadow-xs active:scale-95">
                <Upload className="w-4 h-4" />
                {currentDoc ? 'เปลี่ยนไฟล์เอกสารสแกน' : 'อัปโหลดเอกสารสแกน'}
                <input
                  type="file"
                  accept="image/*,application/pdf"
                  className="hidden"
                  onChange={handleFileUpload}
                  disabled={isSaving}
                />
              </label>
            )}

            {!readOnly && currentDoc && onSave && (
              <button
                type="button"
                onClick={handleRemoveDoc}
                disabled={isSaving}
                className="px-3 py-2 text-xs font-medium rounded-xl border border-rose-300 dark:border-rose-900/50 text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors flex items-center gap-1.5 cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5" /> ลบไฟล์
              </button>
            )}
          </div>

          <button
            onClick={onClose}
            className="w-full sm:w-auto px-5 py-2 text-xs font-semibold rounded-xl bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 transition-colors cursor-pointer"
          >
            ปิดหน้าต่าง
          </button>
        </div>

      </div>
    </div>
  );
};

