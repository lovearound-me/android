package me.lovearound.android.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.common.util.concurrent.ListenableFuture
import me.lovearound.android.Palette
import me.lovearound.android.R
import me.lovearound.android.WidgetData
import me.lovearound.android.tr

/**
 * Outside the app, as on the Apple Watch (ios/WatchWidgets): a ♥ in my
 * color on the watch face, and a tile with four close people. Both open
 * the app. Never who, when, or how many.
 */
object Surfaces {
    fun refresh(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(CloseTile::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, HeartComplication::class.java)).requestUpdateAll()
        }
    }

    private fun open(context: Context): ModifiersBuilders.Clickable = ModifiersBuilders.Clickable.Builder()
        .setId("open")
        .setOnClick(ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                .setPackageName(context.packageName)
                .setClassName(MainActivity::class.java.name)
                .build())
            .build())
        .build()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 1): LayoutElement =
        Text.Builder().setText(value).setMaxLines(lines)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .setFontStyle(FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                .build())
            .build()

    private fun bubble(person: WidgetData.Person, size: Float): LayoutElement {
        val circle = Box.Builder().setWidth(dp(size)).setHeight(dp(size))
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setBackground(ModifiersBuilders.Background.Builder()
                    .setColor(argb(Palette.all[Palette.index(person.color)].argb.toInt()))
                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(size / 2)).build())
                    .build())
                .build())
            .addContent(text(person.initial, size * 0.4f, WHITE, bold = true))
            .build()
        val name = Box.Builder().setWidth(dp(64f)).addContent(text(person.label, 12f, NAME)).build()
        return Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(circle)
            .addContent(Spacer.Builder().setHeight(dp(3f)).build())
            .addContent(name)
            .build()
    }

    /** «Love Around ♥», then two rows of two close people; signed out, a word on it. */
    fun tileLayout(context: Context, people: List<WidgetData.Person>?, myColor: Int, screenDp: Int = 227): LayoutElement {
        // A 41 mm Pixel Watch is 192 dp across: smaller bubbles keep the names off the edge.
        val size = if (screenDp < 210) 38f else 44f
        val column = Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        column.addContent(Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(text("Love Around", 14f, WHITE, bold = true))
            .addContent(Spacer.Builder().setWidth(dp(5f)).build())
            .addContent(text("♥", 14f, Palette.all[Palette.index(myColor)].argb.toInt(), bold = true))
            .build())
        column.addContent(Spacer.Builder().setHeight(dp(10f)).build())
        if (people.isNullOrEmpty()) {
            column.addContent(Box.Builder().setWidth(dp(150f)).addContent(
                text(if (people == null) tr("Войди в Love Around на часах") else tr("Пока никого нет"), 14f, MUTED, lines = 3)).build())
        } else {
            for ((r, pair) in people.take(4).chunked(2).withIndex()) {
                if (r > 0) column.addContent(Spacer.Builder().setHeight(dp(8f)).build())
                val row = Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
                for ((i, person) in pair.withIndex()) {
                    if (i > 0) row.addContent(Spacer.Builder().setWidth(dp(6f)).build())
                    row.addContent(bubble(person, size))
                }
                column.addContent(row.build())
            }
        }
        return Box.Builder().setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open(context)).build())
            .addContent(column.build())
            .build()
    }

    const val RESOURCES = "1"
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val NAME = 0xFFF3E6DC.toInt()
    private const val MUTED = 0xFFC7B0A2.toInt()
}

private fun <T> now(value: T): ListenableFuture<T> = CallbackToFutureAdapter.getFuture { it.set(value); "now" }

/** The Smart Stack's «Близкие»: four close people; a tap opens the app. */
class CloseTile : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val layout = Surfaces.tileLayout(this, WidgetData.people(this), WidgetData.myColor(this),
            requestParams.deviceConfiguration.screenWidthDp)
        return now(TileBuilders.Tile.Builder()
            .setResourcesVersion(Surfaces.RESOURCES)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build())
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        now(ResourceBuilders.Resources.Builder().setVersion(Surfaces.RESOURCES).build())
}

/** A ♥ in my color on the watch face: a tap opens the app. */
class HeartComplication : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? = data(request.complicationType)

    private fun data(type: ComplicationType): ComplicationData? {
        val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val said = PlainComplicationText.Builder("Love Around").build()
        val heart = Icon.createWithResource(this, R.drawable.ic_heart)
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder("♥").build(), said)
                .setTapAction(tap).build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(MonochromaticImage.Builder(heart).build(), said)
                .setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE -> {
                val colored = Icon.createWithResource(this, R.drawable.ic_heart)
                    .setTint(Palette.all[Palette.index(WidgetData.myColor(this))].argb.toInt())
                SmallImageComplicationData.Builder(SmallImage.Builder(colored, SmallImageType.ICON).build(), said)
                    .setTapAction(tap).build()
            }
            else -> null
        }
    }
}
