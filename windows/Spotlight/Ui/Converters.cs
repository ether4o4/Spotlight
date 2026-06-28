using System.Globalization;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using NeverSoft.Spotlight.Models;

namespace NeverSoft.Spotlight.Ui;

/// <summary>Maps a <see cref="ResultType"/> to its fallback vector <see cref="Geometry"/>.</summary>
public sealed class TypeToGeometryConverter : IValueConverter
{
    private static readonly Dictionary<ResultType, Geometry> Cache = new();

    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is not ResultType type) return null;
        if (!Cache.TryGetValue(type, out var geo))
        {
            geo = Geometry.Parse(Glyphs.For(type));
            geo.Freeze();
            Cache[type] = geo;
        }
        return geo;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => Binding.DoNothing;
}

/// <summary>Maps a <see cref="ResultType"/> to its readable label ("App", "Image", ...).</summary>
public sealed class TypeToLabelConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        => value is ResultType type ? Glyphs.Label(type) : string.Empty;

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => Binding.DoNothing;
}

/// <summary>Visible when the bound value is non-null; collapsed otherwise. Pass "Invert" to flip.</summary>
public sealed class NullToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        bool hasValue = value != null;
        if (string.Equals(parameter as string, "Invert", StringComparison.OrdinalIgnoreCase))
            hasValue = !hasValue;
        return hasValue ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
        => Binding.DoNothing;
}
