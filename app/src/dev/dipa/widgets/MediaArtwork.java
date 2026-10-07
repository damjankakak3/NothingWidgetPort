package dev.dipa.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.text.TextPaint;
import android.text.TextUtils;
import java.util.Map;
import java.util.WeakHashMap;

/** Static Nothing media pages. Session callbacks drive existing widget updates. */
public final class MediaArtwork {
    private static final int MAX_EDGE = 768;
    private static final Map<Bitmap, Bitmap> SOFTWARE_COVERS = new WeakHashMap<>();
    private static Typeface regular;
    private static Typeface light;
    private MediaArtwork() {}

    public static Bitmap render(Context c, int widthPx, int heightPx, MediaController controller) {
        return render(c, widthPx, heightPx, controller, 0);
    }

    /** Page zero is the original full album cover; page one is its metadata page. */
    public static Bitmap render(Context c, int widthPx, int heightPx, MediaController controller, int page) {
        int requestedW = Math.max(1, widthPx), requestedH = Math.max(1, heightPx);
        float bound = Math.min(1f, MAX_EDGE / (float) Math.max(requestedW, requestedH));
        int width = Math.max(1, Math.round(requestedW * bound));
        int height = Math.max(1, Math.round(requestedH * bound));
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        float side = Math.min(width, height), left = (width - side) / 2f, top = (height - side) / 2f;
        canvas.translate(left, top);
        float radius = side * 20f / 172f;
        Paint fill = paint(1, color(c, "color_media_container_bg", dark(c) ? 0xff262523 : 0xfff5f3f0));
        RectF card = new RectF(0, 0, side, side);
        canvas.drawRoundRect(card, radius, radius, fill);
        Path outline = new Path(); outline.addRoundRect(card, radius, radius, Path.Direction.CW);
        canvas.clipPath(outline);
        MediaMetadata metadata = null;
        PlaybackState state = null;
        if (controller != null) try {
            metadata = controller.getMetadata(); state = controller.getPlaybackState();
        } catch (RuntimeException ignored) {}
        Bitmap cover = albumCover(metadata);
        if (Math.floorMod(page, 2) == 1 && metadata != null)
            drawMetadata(c, canvas, side, controller, metadata, state, cover);
        else {
            boolean drawn = drawCover(canvas, cover, card);
            if (!drawn) {
                if (controller == null) {
                    if (!drawable(c, canvas, "media_initial_music_cover", card)) drawDefaultIcon(c, canvas, side);
                } else drawDefaultIcon(c, canvas, side);
            }
            if (controller != null) drawWaveform(c, canvas, image, left, top, side);
        }
        return image;
    }

    private static Bitmap albumCover(MediaMetadata metadata) {
        if (metadata == null) return null;
        for (String key : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            try {
                Bitmap art = metadata.getBitmap(key);
                if (art != null && !art.isRecycled() && art.getWidth() > 0 && art.getHeight() > 0) return art;
            } catch (RuntimeException ignored) {}
        }
        return null;
    }

    private static boolean drawCover(Canvas canvas, Bitmap cover, RectF bounds) {
        if (cover == null || cover.isRecycled()) return false;
        try {
            Bitmap readable = softwareCover(cover);
            if (readable == null || readable.isRecycled()) return false;
            int crop = Math.min(readable.getWidth(), readable.getHeight());
            int left = (readable.getWidth() - crop) / 2, top = (readable.getHeight() - crop) / 2;
            canvas.drawBitmap(readable, new Rect(left, top, left + crop, top + crop), bounds,
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    private static synchronized Bitmap softwareCover(Bitmap cover) {
        if (cover.getConfig() != Bitmap.Config.HARDWARE) return cover;
        Bitmap cached = SOFTWARE_COVERS.get(cover);
        if (cached != null && !cached.isRecycled()) return cached;
        // Crop and transform together; only the central cover region is read back.
        int crop = Math.min(cover.getWidth(), cover.getHeight());
        Matrix transform = new Matrix(); float factor = Math.min(1f, MAX_EDGE / (float) crop);
        transform.setScale(factor, factor);
        Bitmap bounded = Bitmap.createBitmap(cover, (cover.getWidth() - crop) / 2,
                (cover.getHeight() - crop) / 2, crop, crop, transform, true);
        if (bounded.getConfig() == Bitmap.Config.HARDWARE) {
            Bitmap software = bounded.copy(Bitmap.Config.ARGB_8888, false);
            if (bounded != cover) bounded.recycle();
            bounded = software;
        }
        if (bounded != null) SOFTWARE_COVERS.put(cover, bounded);
        return bounded;
    }

    private static void drawDefaultIcon(Context c, Canvas canvas, float side) {
        // MediaPlayer view/p.c.J: 189 by 168 px on the original 452 px card.
        float w = side * 189f / 452f, h = side * 168f / 452f;
        RectF bounds = new RectF((side - w) / 2f, (side - h) / 2f, (side + w) / 2f, (side + h) / 2f);
        if (drawable(c, canvas, "music_cover_default", bounds)) return;
        Paint ink = paint(1, color(c, "color_media_album_icon_default", dark(c) ? 0xfff5f3f0 : 0xff262523));
        // Exact dots from donor music_cover_default.xml, fallback when assets are absent.
        int[][] points = {{20,36},{60,36},{20,44},{60,44},{20,52},{60,52},{20,28},{60,28},
                {28,12},{28,20},{52,4},{36,4},{20,4},{20,20},{60,20},{20,12},{60,12},{60,4},
                {44,4},{28,4},{36,12},{36,20},{44,12},{44,20},{52,12},{52,20},
                {12,52},{52,52},{4,52},{44,52},{12,60},{52,60},{4,60},{44,60}};
        float sx = w / 72f, sy = h / 64f;
        for (int[] p : points) canvas.drawOval(new RectF(bounds.left + (p[0] - 4) * sx,
                bounds.top + (p[1] - 4) * sy, bounds.left + (p[0] + 4) * sx,
                bounds.top + (p[1] + 4) * sy), ink);
    }

    private static void drawWaveform(Context c, Canvas canvas, Bitmap output, float offsetX, float offsetY, float side) {
        float scale = side / 452f;
        // The source Lottie frame zero consists of these four 4 px circles on a 48 px canvas.
        float waveSide = 126f * scale, x = 11f * scale, y = side - 11f * scale - waveSide;
        int fallback = color(c, "color_music_waveform_default", dark(c) ? 0xfff5f3f0 : 0xff262523);
        int sample = output.getPixel(Math.max(0, Math.min(output.getWidth() - 1,
                Math.round(offsetX + x + waveSide / 2f))), Math.max(0,
                Math.min(output.getHeight() - 1, Math.round(offsetY + y + waveSide * 2f / 3f))));
        double brightness = (Color.red(sample) * .2126 + Color.green(sample) * .7152 + Color.blue(sample) * .0722) / 255d;
        Paint p = paint(1, Color.alpha(sample) < 128 ? fallback : brightness > .55 ? 0xff262523 : 0xfff5f3f0);
        for (int center : new int[]{15, 21, 27, 33})
            canvas.drawCircle(x + center * waveSide / 48f, y + 32f * waveSide / 48f, 2f * waveSide / 48f, p);
    }

    private static void drawMetadata(Context c, Canvas canvas, float side, MediaController controller,
            MediaMetadata metadata, PlaybackState state, Bitmap cover) {
        float scale = side / 452f, margin = 42f * scale;
        RectF album = new RectF(margin, margin, margin + 184f * scale, margin + 184f * scale);
        canvas.save(); Path thumbnail = new Path();
        thumbnail.addRoundRect(album, 16f * scale, 16f * scale, Path.Direction.CW); canvas.clipPath(thumbnail);
        if (!drawCover(canvas, cover, album)) drawable(c, canvas, "music_album_cover_icon_default", album);
        canvas.restore();
        if (controller != null) try {
            Drawable app = c.getPackageManager().getApplicationIcon(controller.getPackageName());
            float iconSide = (126f - 52f) * scale;
            float iconLeft = side - (11f + 26f) * scale - iconSide;
            app.setBounds(Math.round(iconLeft), Math.round(37f * scale),
                    Math.round(iconLeft + iconSide), Math.round(37f * scale + iconSide)); app.draw(canvas);
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ignored) {}
        int ink = color(c, "color_album_artist_text", dark(c) ? 0xfff5f3f0 : 0xff262523);
        Paint title = paint(42f * scale, ink), artist = paint(37f * scale, ink);
        title.setTypeface(font(c, false)); artist.setTypeface(font(c, true));
        float top = album.bottom + 32f * scale, available = side - margin * 2f;
        float titleBaseline = top - title.getFontMetrics().top;
        text(canvas, first(metadata, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                margin, titleBaseline, available, title);
        float artistBaseline = titleBaseline + title.getFontMetrics().bottom - artist.getFontMetrics().top;
        text(canvas, first(metadata, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM), margin, artistBaseline, available, artist);
        float barY = artistBaseline + artist.getFontMetrics().bottom + 32f * scale;
        float barHeight = side * 2f / 172f;
        Paint p = paint(1, color(c, "color_music_progress_bar_bg", dark(c) ? 0xff44413d : 0xffddd9d3));
        canvas.drawRect(margin, barY, side - margin, Math.min(side - margin, barY + barHeight), p);
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = state == null ? 0 : Math.max(0, state.getPosition());
        if (duration > 0) {
            p.setColor(color(c, "color_music_progress_bar", ink));
            canvas.drawRect(margin, barY, margin + available * Math.min(1, position / (float) duration),
                    Math.min(side - margin, barY + barHeight), p);
        }
    }

    private static CharSequence first(MediaMetadata metadata, String... keys) {
        for (String key : keys) {
            CharSequence value = metadata.getText(key);
            if (value != null && value.length() > 0) return value;
        }
        return "";
    }
    private static void text(Canvas canvas, CharSequence label, float x, float baseline, float maxWidth, Paint paint) {
        TextPaint textPaint = new TextPaint(paint);
        CharSequence fitted = TextUtils.ellipsize(label == null ? "" : label, textPaint, Math.max(1, maxWidth), TextUtils.TruncateAt.END);
        canvas.drawText(fitted.toString(), x, baseline, paint);
    }
    private static synchronized Typeface font(Context c, boolean thin) {
        if (regular == null) {
            int id = c.getResources().getIdentifier("roboto_regular", "font", c.getPackageName());
            regular = id == 0 ? Typeface.create("sans-serif", Typeface.NORMAL) : c.getResources().getFont(id);
        }
        if (!thin) return regular;
        if (light == null) light = Typeface.create(regular, 300, false);
        return light;
    }
    private static boolean drawable(Context c, Canvas canvas, String name, RectF bounds) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        if (id == 0) return false;
        Drawable d = c.getDrawable(id); if (d == null) return false;
        d.setBounds(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        d.draw(canvas); return true;
    }
    private static Paint paint(float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTextSize(size); p.setColor(color); return p;
    }
    private static int color(Context c, String name, int fallback) {
        int id = c.getResources().getIdentifier(name, "color", c.getPackageName());
        return id == 0 ? fallback : c.getColor(id);
    }
    private static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
