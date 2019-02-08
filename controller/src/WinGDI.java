import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;

public interface WinGDI extends Library {
    WinGDI INSTANCE = Native.load("gdi32", WinGDI.class);

    WinDef.DWORD GetPixel(WinDef.HDC hdc, int x, int y);
}
