# -*- coding: utf-8 -*-
"""
Module Tra Cứu Phạt Nguội Cá Nhân Dành Cho Xe Máy - PMap
Hỗ trợ kiểm tra biên bản vi phạm từ Cục Cảnh sát giao thông (csgt.vn)
"""

import urllib.parse
import webbrowser
import sys

def get_csgt_lookup_url(plate_number: str) -> str:
    """
    Tạo đường link tra cứu chính thức trực tiếp trên Cổng CSGT
    Loại phương tiện: 2 = Xe máy / Mô tô
    """
    clean_plate = plate_number.replace("-", "").replace(".", "").replace(" ", "").upper()
    base_url = "https://www.csgt.vn/tra-cuu-phuong-tien-vi-pham.html"
    params = {
        "LoaiXe": "2",       # 2 là Xe máy / Mô tô theo chuẩn CSGT
        "BienKiemSoat": clean_plate
    }
    return f"{base_url}?{urllib.parse.urlencode(params)}"

def open_lookup(plate_number: str):
    url = get_csgt_lookup_url(plate_number)
    print(f"\n[PMAP] Đang mở cổng tra cứu phạt nguội Cục CSGT cho xe máy biển số: {plate_number}")
    print(f"[LINK] {url}")
    webbrowser.open(url)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        plate = sys.argv[1]
    else:
        plate = input("Nhập biển số xe máy của bạn (Ví dụ: 59X3-12345): ").strip()
    if plate:
        open_lookup(plate)
