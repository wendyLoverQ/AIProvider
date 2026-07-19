using System.Runtime.InteropServices;
using System.Security.Cryptography;

sealed class WallpaperService
{
    private const long MaxWallpaperBytes = 120L * 1024 * 1024;
    private readonly string storageDirectory;

    public WallpaperService()
    {
        storageDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "AIProvider", "Wallpapers");
    }

    public IReadOnlyList<WallpaperMonitor> Monitors()
    {
        EnsureWindows();
        return RunSta(() =>
        {
            var desktop = CreateDesktopWallpaper();
            try
            {
                desktop.GetMonitorDevicePathCount(out var count);
                var monitors = new List<WallpaperMonitor>();
                for (uint index = 0; index < count; index++)
                {
                    desktop.GetMonitorDevicePathAt(index, out var id);
                    desktop.GetMonitorRECT(id, out var rect);
                    var primary = rect.Left <= 0 && rect.Right > 0 && rect.Top <= 0 && rect.Bottom > 0;
                    monitors.Add(new WallpaperMonitor(id, (int)index + 1, $"显示器 {index + 1}",
                        Math.Max(1, rect.Right - rect.Left), Math.Max(1, rect.Bottom - rect.Top), primary));
                }
                return (IReadOnlyList<WallpaperMonitor>)monitors;
            }
            finally { Marshal.FinalReleaseComObject(desktop); }
        });
    }

    public async Task<string> ApplyAsync(IFormFile file, string monitorId)
    {
        EnsureWindows();
        if (file == null || file.Length <= 0) throw new ArgumentException("壁纸文件不能为空");
        if (file.Length > MaxWallpaperBytes) throw new ArgumentException("壁纸文件不能超过 120MB");
        if (string.IsNullOrWhiteSpace(monitorId)) throw new ArgumentException("必须选择显示器");
        if (!Monitors().Any(monitor => string.Equals(monitor.Id, monitorId, StringComparison.Ordinal)))
            throw new ArgumentException("所选显示器已经不可用，请刷新后重试");

        Directory.CreateDirectory(storageDirectory);
        var temporary = Path.Combine(storageDirectory, $".wallpaper-{Guid.NewGuid():N}.tmp");
        try
        {
            await using (var target = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                await file.CopyToAsync(target);
            ValidatePng(temporary);
            var hash = await Sha256Async(temporary);
            var destination = Path.Combine(storageDirectory, $"{hash}.png");
            if (File.Exists(destination)) File.Delete(temporary);
            else File.Move(temporary, destination);
            RunSta(() =>
            {
                var desktop = CreateDesktopWallpaper();
                try { desktop.SetWallpaper(monitorId, destination); }
                finally { Marshal.FinalReleaseComObject(desktop); }
                return true;
            });
            return destination;
        }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }

    private static void ValidatePng(string path)
    {
        Span<byte> signature = stackalloc byte[8];
        using var input = File.OpenRead(path);
        if (input.Read(signature) != signature.Length || !signature.SequenceEqual(new byte[] { 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a }))
            throw new ArgumentException("壁纸必须是本页面生成的真实 PNG 图片");
    }

    private static async Task<string> Sha256Async(string path)
    {
        await using var input = File.OpenRead(path);
        using var sha = SHA256.Create();
        return Convert.ToHexString(await sha.ComputeHashAsync(input)).ToLowerInvariant();
    }

    private static IDesktopWallpaper CreateDesktopWallpaper()
    {
        var type = Type.GetTypeFromCLSID(new Guid("C2CF3110-460E-4FC1-B9D0-8A1C0C9CC4BD"), throwOnError: true)!;
        return (IDesktopWallpaper)(Activator.CreateInstance(type) ?? throw new InvalidOperationException("Windows 壁纸服务不可用"));
    }
    private static T RunSta<T>(Func<T> action)
    {
        T? result = default; Exception? error = null;
        var thread = new Thread(() => { try { result = action(); } catch (Exception exception) { error = exception; } });
        thread.SetApartmentState(ApartmentState.STA); thread.Start(); thread.Join();
        if (error != null) throw new InvalidOperationException("Windows 壁纸服务调用失败", error);
        return result!;
    }
    private static void EnsureWindows()
    {
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("应用壁纸当前仅支持 Windows 本机 Bridge");
    }
}

sealed record WallpaperMonitor(string Id, int Number, string Label, int Width, int Height, bool Primary);

[StructLayout(LayoutKind.Sequential)]
struct WallpaperRect { public int Left, Top, Right, Bottom; }

[ComImport, Guid("B92B56A9-8B55-4E14-9A89-0199BBB6F93B"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IDesktopWallpaper
{
    void SetWallpaper([MarshalAs(UnmanagedType.LPWStr)] string monitorId, [MarshalAs(UnmanagedType.LPWStr)] string wallpaper);
    void GetWallpaper([MarshalAs(UnmanagedType.LPWStr)] string monitorId, [MarshalAs(UnmanagedType.LPWStr)] out string wallpaper);
    void GetMonitorDevicePathAt(uint monitorIndex, [MarshalAs(UnmanagedType.LPWStr)] out string monitorId);
    void GetMonitorDevicePathCount(out uint count);
    void GetMonitorRECT([MarshalAs(UnmanagedType.LPWStr)] string monitorId, out WallpaperRect displayRect);
}
