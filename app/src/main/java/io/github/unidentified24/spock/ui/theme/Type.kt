package io.github.unidentified24.spock.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import io.github.unidentified24.spock.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun spockFont(weight:Int)=Font(R.font.montserrat_variable,FontWeight(weight),
    variationSettings=FontVariation.Settings(FontVariation.weight(weight)))
val SpockType=FontFamily(spockFont(400),spockFont(500),spockFont(600),spockFont(700))
private val body=TextStyle(fontFamily=SpockType)
private val heading=body.copy(fontWeight=FontWeight.Bold,letterSpacing=(-1.2).sp)
val Typography=Typography(
    displayLarge=heading.copy(fontSize=58.sp,lineHeight=64.sp),displayMedium=heading.copy(fontSize=44.sp,lineHeight=50.sp),displaySmall=heading.copy(fontSize=34.sp,lineHeight=40.sp),
    headlineLarge=heading.copy(fontSize=30.sp,lineHeight=36.sp),headlineMedium=heading.copy(fontSize=26.sp,lineHeight=32.sp),headlineSmall=heading.copy(fontSize=22.sp,lineHeight=29.sp),
    titleLarge=heading.copy(fontSize=20.sp,lineHeight=28.sp,letterSpacing=(-.5).sp),titleMedium=body.copy(fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold),
    titleSmall=body.copy(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold),bodyLarge=body.copy(fontSize=16.sp,lineHeight=25.sp),bodyMedium=body.copy(fontSize=14.sp,lineHeight=22.sp),
    bodySmall=body.copy(fontSize=12.sp,lineHeight=18.sp),labelLarge=body.copy(fontSize=14.sp,fontWeight=FontWeight.SemiBold),labelMedium=body.copy(fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold),
    labelSmall=body.copy(fontSize=10.sp,lineHeight=16.sp,letterSpacing=1.5.sp,fontWeight=FontWeight.SemiBold))

fun appearanceTypography(style:String):Typography {
    if(style=="MODERN")return Typography
    val family=when(style){"SERIF"->FontFamily.Serif;"MONO"->FontFamily.Monospace;else->FontFamily.SansSerif}
    return Typography.copy(displayLarge=Typography.displayLarge.copy(fontFamily=family),displayMedium=Typography.displayMedium.copy(fontFamily=family),displaySmall=Typography.displaySmall.copy(fontFamily=family),headlineLarge=Typography.headlineLarge.copy(fontFamily=family),headlineMedium=Typography.headlineMedium.copy(fontFamily=family),headlineSmall=Typography.headlineSmall.copy(fontFamily=family),titleLarge=Typography.titleLarge.copy(fontFamily=family),titleMedium=Typography.titleMedium.copy(fontFamily=family),titleSmall=Typography.titleSmall.copy(fontFamily=family),bodyLarge=Typography.bodyLarge.copy(fontFamily=family),bodyMedium=Typography.bodyMedium.copy(fontFamily=family),bodySmall=Typography.bodySmall.copy(fontFamily=family),labelLarge=Typography.labelLarge.copy(fontFamily=family),labelMedium=Typography.labelMedium.copy(fontFamily=family),labelSmall=Typography.labelSmall.copy(fontFamily=family))
}
