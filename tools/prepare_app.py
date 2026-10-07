"""Build a small, reviewable resource set from the decompiled official widget APK.

Copies only referenced original artwork/fonts. No original DEX, native libraries,
analytics, online config, community code, or CardService code enters the new app.
"""
from pathlib import Path
import re, shutil, json, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DONOR = ROOT / 'analysis/widgets-decoded/res'
DEST = ROOT / 'app/res'
if DEST.exists(): shutil.rmtree(DEST)
DEST.mkdir(parents=True, exist_ok=True)
ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)
values = {}
for folder in DONOR.glob('values*'):
    for path in folder.glob('*.xml'):
        root = ET.parse(path).getroot()
        for child in root:
            if child.tag in ('public', 'public-group', 'overlayable'): continue
            name = child.get('name')
            if name:
                kind = child.get('type', child.tag)
                values.setdefault((kind, name), []).append((folder.name, child))
copied, selected, dependencies = set(), {}, []
WEATHER_DONOR=ROOT/'analysis/weather-latest-decoded/res'
weather_values={}
if WEATHER_DONOR.exists():
    for folder in WEATHER_DONOR.glob('values*'):
        for path in folder.glob('*.xml'):
            for child in ET.parse(path).getroot():
                if child.tag=='public':continue
                if child.get('name'):weather_values.setdefault((child.get('type',child.tag),child.get('name')),[]).append((folder.name,child))
def weather_references(text):
    def reference(m):
        copy_weather(m.group(1),m.group(2))
        return '@'+m.group(1)+'/weather_'+m.group(2)
    return re.sub(r'@([a-zA-Z]+)/([\w.$]+)',reference,text)
def copy_weather(kind,name):
    key=(kind,'weather_'+name)
    if key in copied:return
    copied.add(key)
    for folder in WEATHER_DONOR.glob(kind+'*'):
        for path in folder.glob(name+'.*'):
            target=DEST/folder.name/('weather_'+path.name);target.parent.mkdir(parents=True,exist_ok=True)
            if path.suffix=='.xml':target.write_text(weather_references(path.read_text()))
            else:shutil.copy2(path,target)
            dependencies.append('NothingWeather/'+str(path.relative_to(WEATHER_DONOR)))
    for qualifier,node in weather_values.get((kind,name),[]):
        clone=ET.fromstring(weather_references(ET.tostring(node,encoding='unicode')))
        clone.set('name','weather_'+name)
        selected.setdefault(qualifier,{})[key]=clone
def copy_resource(kind, name):
    key = (kind, name)
    if key in copied: return
    copied.add(key)
    for folder in DONOR.glob(kind + '*'):
        if folder.name.startswith('values'): continue
        for path in folder.glob(name + '.*'):
            target = DEST / folder.name / path.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            dependencies.append(str(path.relative_to(DONOR)))
            if path.suffix == '.xml': scan(path.read_text())
    for qualifier, node in values.get(key, []):
        selected.setdefault(qualifier, {})[key] = node
        scan(ET.tostring(node, encoding='unicode'))
def scan(text):
    for kind, name in re.findall(r'@([a-zA-Z]+)/([\w.$]+)', text): copy_resource(kind, name)
def write(kind, name, text):
    copied.add((kind, name))
    path = DEST / kind / (name + '.xml')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    scan(text)

variants = [
 ('AnalogBold','Analog clock · Bold','Clocks',2,2,'analog_bold'),
 ('AnalogScale','Analog clock · Marks','Clocks',2,2,'analog_scale'),
 ('DigitalSquare','Digital clock · Stacked','Clocks',2,2,'digital_square'),
 ('DigitalWide','Digital clock · Wide','Clocks',2,1,'digital_wide'),
 ('World','World clock','Clocks',2,2,'world'),
 ('Date','Date','Date',2,2,'date'),
 ('QuickTransparent','Quick Look · Transparent','Quick Look',4,1,'quick_transparent'),
 ('QuickCard','Quick Look · Card','Quick Look',2,2,'quick_card'),
 ('PhotoCircle','Photos · Circle','Photos',2,2,'photo_circle'),
 ('PhotoRectangle','Photos · Rectangle','Photos',2,2,'photo_rectangle'),
 ('Compass','Compass','Compass',2,2,'compass'),
 ('CompassSmall','Compass · Small','Compass',1,1,'compass'),
 ('Steps','Steps','Steps',2,2,'steps'),
 ('StepsProgress','Steps · Goal progress','Steps',2,1,'steps_progress'),
 ('StepsDaily','Steps · Daily count','Steps',2,1,'steps_daily'),
 ('StepsStreak','Steps · Weekly streak','Steps',2,1,'steps_streak'),
 ('ScreenTime','Screen time','Screen time',2,2,'screen'),
 ('ScreenDuration','Screen time · Duration','Screen time',2,1,'screen_duration'),
 ('ScreenShape','Screen time · Usage','Screen time',2,1,'screen_shape'),
 ('ScreenFace','Screen time · Face','Screen time',1,1,'screen_face'),
 ('Media','Media player','Media',2,2,'media'),
 ('Battery','Battery · Devices','Battery',2,2,'battery'),
 ('BatterySmall','Battery · Phone','Battery',1,1,'battery_small'),
 ('Countdown','Countdown','Countdown',2,2,'countdown'),
 ('BreathRelax','Breathing · Relax','Breathing',2,2,'breath_relax'),
 ('BreathFocus','Breathing · Focus','Breathing',2,2,'breath_focus'),
 ('BreathCalm','Breathing · Calm','Breathing',2,2,'breath_calm'),
 ('WeatherCircle','Weather · Circle','Weather',2,2,'weather_circle'),
 ('WeatherSquare','Weather · Square','Weather',2,2,'weather_square'),
 ('WeatherWide','Weather · Forecast','Weather',4,2,'weather_wide'),
 ('WeatherCombo','Weather · Combo','Weather',2,2,'weather_combo'),
]
(ROOT/'source/widget-variants.json').write_text(json.dumps(variants, indent=2)+'\n')

def frame(body, bg='@drawable/card_bg', padding='12dp'):
    return f'<FrameLayout xmlns:android="{ANDROID}" android:id="@+id/root" android:layout_width="match_parent" android:layout_height="match_parent"><FrameLayout android:id="@+id/content" android:layout_gravity="center" android:layout_width="match_parent" android:layout_height="match_parent" android:background="{bg}" android:clipToOutline="true" android:padding="{padding}">{body}</FrameLayout></FrameLayout>'
def text(ident, size, label='', font='ntype82', extra=''):
    if ident in ('subtitle','detail','forecast'):
        return f'<FrameLayout android:id="@+id/{ident}" android:layout_width="match_parent" android:layout_height="{max(16,round(size*1.5))}dp" {extra}><TextView android:id="@+id/{ident}_text" android:layout_width="match_parent" android:layout_height="match_parent" android:textSize="{size}sp" android:textColor="@color/date_date_text_color" android:fontFamily="monospace" android:gravity="center" android:includeFontPadding="false" android:text="{label}"/><ImageView android:id="@+id/{ident}_image" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="fitCenter" android:tint="@color/date_date_text_color" android:visibility="gone" android:tag="{size}"/></FrameLayout>'
    return f'<TextView android:id="@+id/{ident}" android:layout_width="match_parent" android:layout_height="wrap_content" android:textSize="{size}sp" android:textColor="#f5f3f0" android:fontFamily="{font}" android:gravity="center" android:includeFontPadding="false" android:maxLines="1" android:text="{label}" {extra}/>'
def column(body): return f'<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center">{body}</LinearLayout>'
def clock(ident, fmt12, fmt24, size, font='nothingdot57aligned', gravity='center', width='match_parent'):
    return f'<TextClock android:id="@+id/{ident}" android:layout_width="{width}" android:layout_height="wrap_content" android:format12Hour="{fmt12}" android:format24Hour="{fmt24}" android:gravity="{gravity}" android:fontFamily="{font}" android:textFontWeight="400" android:includeFontPadding="false" android:maxLines="1" android:textAllCaps="true" android:textSize="{size}sp" android:textColor="@color/date_date_text_color"/>'
def native_text(ident,label,size,font='nothingdot',gravity='start',width='match_parent',font_padding=False):
    padding='true' if font_padding else 'false'
    return f'<TextView android:id="@+id/{ident}" android:layout_width="{width}" android:layout_height="wrap_content" android:text="{label}" android:textSize="{size}sp" android:textColor="@color/date_date_text_color" android:fontFamily="{font}" android:textFontWeight="400" android:gravity="{gravity}" android:includeFontPadding="{padding}" android:maxLines="1" android:ellipsize="end"/>'
for kind in ['bold','scale']:
    dial='analog_clock_scale_dial' if kind=='scale' else 'analog_clock_bg'
    write('layout','analog_'+kind,frame(f'<AnalogClock android:id="@+id/clock" android:layout_width="match_parent" android:layout_height="match_parent" android:dial="@drawable/{dial}" android:hand_hour="@drawable/analog_clock_{kind}_bitmap_hour" android:hand_minute="@drawable/analog_clock_{kind}_bitmap_minute" android:hand_second="@drawable/analog_clock_{kind}_bitmap_second" android:contentDescription="Current time"/>','@drawable/bg_card_analog_clock','0dp'))
write('layout','digital_square', frame(column(clock('clock','hh','HH',60)+clock('minutes','mm','mm',60)),'@drawable/digital_clock_widget_bg','0dp'))
write('layout','digital_wide',frame('<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="horizontal" android:gravity="center">'+clock('clock','hh','HH',40,width='wrap_content')+native_text('time_colon',':',40,'nothingdot57aligned','center','wrap_content')+clock('minutes','mm','mm',40,width='wrap_content')+'</LinearLayout>','@drawable/digital_clock_widget_bg_2x1','0dp'))
write('layout','world',frame(native_text('subtitle','BUDAPEST',20)+f'<FrameLayout android:layout_width="match_parent" android:layout_height="match_parent" android:layout_gravity="bottom">{clock("clock","hh:mm","HH:mm",40,gravity="start")}</FrameLayout>','@drawable/world_clock_widget_bg_normal','0dp'))
write('layout','date',frame(f'<TextClock android:id="@+id/clock" android:layout_width="match_parent" android:layout_height="match_parent" android:format12Hour="d" android:format24Hour="d" android:fontFamily="ntype82" android:textColor="@color/date_date_text_color" android:textSize="112sp" android:gravity="center" android:includeFontPadding="false" android:maxLines="1"/><TextClock android:id="@+id/date_weekday" android:layout_gravity="top|end" android:layout_width="match_parent" android:layout_height="wrap_content" android:format12Hour="EEE" android:format24Hour="EEE" android:fontFamily="ntype82" android:textColor="@color/date_date_text_color" android:textSize="18sp" android:gravity="end" android:includeFontPadding="false" android:maxLines="1"/>','@drawable/bg_date_2x2_widget','0dp'))
weather_icon='<ImageView android:id="@+id/weather_icon" android:layout_width="20dp" android:layout_height="20dp" android:src="@drawable/ic_widget_clear_night_neutral" android:contentDescription="Weather condition" android:layout_marginEnd="10dp" android:visibility="gone"/>'
write('layout','quick_transparent',frame('<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center_vertical">'+clock('clock','E, d MMM','E, d MMM',20,'nothingdot','start')+'<LinearLayout android:id="@+id/quick_weather_row" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical">'+weather_icon+native_text('subtitle','—',20,font_padding=True)+'</LinearLayout></LinearLayout>','@android:color/transparent','0dp'))
write('layout','quick_card',frame('<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="top">'+clock('clock','EEEE','EEEE',20,'nothingdot','start')+clock('minutes','d MMM','d MMM',20,'nothingdot','start')+native_text('subtitle','—',20)+'<LinearLayout android:id="@+id/quick_temperature" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical">'+weather_icon+native_text('value','—',20,font_padding=True)+'</LinearLayout></LinearLayout>','@drawable/card_bg','0dp'))
for kind,bg in [('circle','@drawable/circle_bg'),('rectangle','@drawable/card_bg')]:
    write('layout','photo_'+kind,frame('<ImageView android:id="@+id/image" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="centerCrop" android:contentDescription="Selected photo"/>'+text('subtitle',18,'Choose a photo',extra='android:layout_gravity="center"'),bg,'0dp'))
write('layout','compass',frame(column(text('subtitle',13,'COMPASS')+text('value',48,'—','@font/ndot_57_aligned')+text('detail',12,'Tap for live compass'))))
write('layout','steps',frame(column(text('subtitle',12,'STEPS')+text('value',42,'0','@font/ndot_57_aligned')+'<ProgressBar android:id="@+id/progress" style="?android:attr/progressBarStyleHorizontal" android:layout_width="match_parent" android:layout_height="5dp" android:progressTint="#cf3f36" android:progressBackgroundTint="#514b48"/>'+text('detail',11,'Tap to enable tracking'))))
write('layout','screen',frame(column(text('subtitle',12,'SCREEN TIME')+text('value',36,'—','@font/ndot_57_aligned')+'<ProgressBar android:id="@+id/progress" style="?android:attr/progressBarStyleHorizontal" android:layout_width="match_parent" android:layout_height="5dp" android:progressTint="#cf3f36"/>'+text('detail',11,'Usage access needed')),'@drawable/screen_time_bg'))
write('layout','screen_face',frame(column(text('value',42,':)','@font/lettera_mono_ll_regular')+text('detail',12,'SCREEN TIME')),'@drawable/screen_time_bg_1_1'))
for name,title in [('steps_progress','STEPS'),('steps_streak','THIS WEEK'),('screen_shape','SCREEN TIME')]:
    write('layout',name,frame(column(text('subtitle',11,title)+'<ImageView android:id="@+id/chart" android:layout_width="match_parent" android:layout_height="32dp" android:contentDescription="Goal progress"/>'+text('detail',11)),'@drawable/card_bg','8dp'))
write('layout','screen_face',frame(column('<ImageView android:id="@+id/face" android:layout_width="match_parent" android:layout_height="56dp" android:src="@drawable/icon_screen_time_expression" android:contentDescription="Screen time status"/>'+text('detail',11)),'@drawable/screen_time_bg_1_1','8dp'))
copy_resource('drawable','icon_screen_time_overtime_expression')
write('layout','battery',frame(column(text('subtitle',11,'BATTERY')+text('value',40,'—','@font/ndot_57_aligned')+'<ProgressBar android:id="@+id/progress" style="?android:attr/progressBarStyleHorizontal" android:layout_width="match_parent" android:layout_height="6dp" android:progressTint="#cf3f36"/>'+text('detail',11)),'@drawable/battery_bg'))
write('layout','countdown',frame(column(text('subtitle',14,'COUNTDOWN')+text('value',50,'—','@font/ndot_57_aligned')+text('detail',12,'Tap to set date'))))
for mode in ['relax','focus','calm']:
    write('layout','breath_'+mode,frame(f'<ImageView android:id="@+id/breath_preview" android:layout_width="match_parent" android:layout_height="match_parent" android:src="@drawable/breath_{mode}_preview" android:scaleType="fitCenter" android:contentDescription="Start {mode} breathing"/>','@android:color/transparent','0dp'))
for shape in ['circle','square','wide','combo']:
    write('layout','weather_'+shape,frame(column(text('subtitle',12,'WEATHER')+text('value',43,'—','@font/ndot_57_aligned')+text('detail',12,'Choose a city')+text('forecast',11)),'@drawable/'+('circle_bg' if shape=='circle' else 'card_bg'),'8dp'))
write('layout','media',frame(column(text('subtitle',14,'MEDIA PLAYER')+text('detail',12,'Enable notification access')+'''<LinearLayout android:layout_width="match_parent" android:layout_height="52dp" android:orientation="horizontal" android:gravity="center">
<ImageButton android:id="@+id/previous" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/media_play_previous_icon" android:background="@android:color/transparent" android:contentDescription="Previous track"/>
<ImageButton android:id="@+id/play" android:layout_width="48dp" android:layout_height="48dp" android:src="@android:drawable/ic_media_play" android:tint="#f5f3f0" android:background="@android:color/transparent" android:contentDescription="Play or pause"/>
<ImageButton android:id="@+id/next" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/media_play_next_icon" android:background="@android:color/transparent" android:contentDescription="Next track"/>
</LinearLayout>'''),'@drawable/media_player_widget_bg_normal'))
write('drawable','card_bg',f'<shape xmlns:android="{ANDROID}"><solid android:color="@color/date_date_bg_color"/><corners android:radius="20dp"/></shape>')
write('drawable','circle_bg',f'<shape xmlns:android="{ANDROID}" android:shape="oval"><solid android:color="@color/date_date_bg_color"/></shape>')
write('drawable','app_icon',f'<vector xmlns:android="{ANDROID}" android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48"><path android:fillColor="#262523" android:pathData="M0,0h48v48h-48z"/><path android:fillColor="#f5f3f0" android:pathData="M11,11h10v10h-10zM27,11h10v10h-10zM11,27h10v10h-10z"/><path android:fillColor="#cf3f36" android:pathData="M27,27h10v10h-10z"/></vector>')

# Faithful custom-view compositions use original Canvas artwork on data changes.
# fitCenter preserves their authored proportions; root/content geometry is host-sized.
for layout in ['battery','battery_small','steps','steps_daily','steps_progress','steps_streak','screen','screen_duration','screen_shape','compass','countdown','weather_circle','weather_square','weather_wide','weather_combo']:
    write('layout',layout,frame('<ImageView android:id="@+id/artwork" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="fitCenter" android:contentDescription="Widget information"/>','@android:color/transparent','0dp'))
write('layout','media',frame('<ImageView android:id="@+id/artwork" android:layout_width="match_parent" android:layout_height="match_parent" android:scaleType="fitCenter" android:contentDescription="Media player"/><LinearLayout android:layout_gravity="bottom" android:layout_width="match_parent" android:layout_height="40dp" android:orientation="horizontal"><FrameLayout android:id="@+id/previous" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:contentDescription="Previous track"/><FrameLayout android:id="@+id/play" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:contentDescription="Play or pause"/><FrameLayout android:id="@+id/next" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:contentDescription="Next track"/></LinearLayout>','@android:color/transparent','0dp'))
for name in ['device_phone_default','battery_indicator_charging','device_uncategorised','device_default_ear','device_watch_pro','device_nothingx_headphone_a','ic_human_walking_1']:
    copy_resource('drawable',name)
for name in ['compass_direction_all','compass_direction_north','compass_direction_east','compass_direction_south','compass_direction_west','compass_direction_all_1x1','compass_arrow','compass_arrow_red','compass_arrow_disable','compass_arrow_1x1','compass_arrow_red_1x1','compass_arrow_disable_1x1','media_initial_music_cover','music_cover_default','music_album_cover_icon_default']:
    copy_resource('drawable',name)
for name in ['color_media_container_bg','color_media_album_icon_default','color_music_waveform_default','color_album_artist_text','color_music_progress_bar','color_music_progress_bar_bg','compass_font_color','compass_bg_color','arrow_color','arrow_red_color']:
    copy_resource('color',name)
copy_resource('color','ql_trans_style_primary_color')
for name in ['sunny','clear_night','partly_cloudy','cloudy_night','overcast','fog','snow','thunderstorms','rainy','cloudy']:
    copy_resource('drawable','ic_widget_'+name+'_neutral')
copy_resource('drawable','ic_widget_sleet_neutral')
for name in ['ic_widget_location_permission_needed','ic_widget_no_connection','ic_widget_temp_arrow_up','ic_widget_temp_arrow_down']:
    copy_weather('drawable',name)
for name in ['widget_icon_color','widget_style_circle_ring_bg_color','widget_rectangle_low_temp_txt_color']:
    copy_weather('color',name)
for name in ['widget_tap_to_add_city','weather_widget_no_connection']:
    copy_weather('string',name)
for name in ['countdown_no_event_description','countdown_hint_today','countdown_hint_until','countdown_hint_since','countdown_display_day','countdown_event_name_empty_placeholder']:
    copy_resource('string',name)
for name in ['ndot','ndot57','ndot_57_aligned','roboto_regular']:
    copy_resource('font',name)
for name in ['date_day_of_week_red_color','pedometer_bg_color','pedometer_text_color','pedometer_walking_circle_color','pedometer_walking_dot_color','pedometer_goal_not_reached_color','pedometer_calender_goal_reached_color','pedometer_calender_currentDay_Color','screen_time_bg_color','screen_time_overtime_bg_color','screen_time_text_color','screen_time_points_progress_progress_color','screen_time_points_progress_track_color','screen_time_points_progress_second_progress_color','screen_time_overtime_item_text_color']:
    copy_resource('color',name)
for name in ['pedometer_todayStepsDes','pedometer_sevenDayAverageDes']:
    copy_resource('string',name)
for name in ['ntype_82_headline','ntype_82_regular']:
    source=ROOT/'analysis/widgets-decoded/assets/fonts'/(name+'.otf')
    if source.exists():
        (DEST/'font').mkdir(exist_ok=True)
        shutil.copy2(source,DEST/'font'/(name+'.otf'))

# Original light/dark colors rather than fixed white on pale donor backgrounds.
copy_resource('color','date_date_text_color')
copy_resource('font','lettera_mono_ll_regular')
for path in (DEST/'layout').glob('*.xml'):
    source=path.read_text().replace('android:textColor="#f5f3f0"','android:textColor="@color/date_date_text_color"')
    # RemoteViews deliberately restricts APK font loading in a different host UID.
    # Evolution X already supplies these real Nothing families in its system map.
    source=source.replace('android:fontFamily="@font/ndot_57_aligned"','android:fontFamily="nothingdot57aligned"')
    source=source.replace('android:fontFamily="@font/lettera_mono_ll_regular"','android:fontFamily="ntype82"')
    if path.stem in ('digital_square','digital_wide','world'):
        source=re.sub(r'android:background="@drawable/[^"]+"','android:background="@drawable/card_bg"',source,count=1)
    if path.stem=='quick_transparent':
        source=source.replace('android:textColor="@color/date_date_text_color"','android:textColor="@color/ql_trans_style_primary_color" android:shadowColor="#8c000000" android:shadowDy="1" android:shadowRadius="0.5"')
    path.write_text(source)

caption_data=[]
for path in (DEST/'layout').glob('*.xml'):
    node=ET.parse(path).getroot()
    for frame_node in node.iter('FrameLayout'):
        ident=frame_node.get(f'{{{ANDROID}}}id','').split('/')[-1]
        if ident not in ('subtitle','detail','forecast'):continue
        image=frame_node.find('ImageView');label=frame_node.find('TextView')
        if image is not None and label is not None:
            caption_data.append([path.stem,ident,image.get(f'{{{ANDROID}}}tag','12'),label.get(f'{{{ANDROID}}}text','')])
caption_java='package dev.dipa.widgets; public final class CaptionData { public static final String[][] ITEMS = {\n'
caption_java+='\n'.join('{'+','.join(json.dumps(v) for v in row)+'},' for row in caption_data)
caption_java+='\n}; }\n'
(ROOT/'app/src/dev/dipa/widgets/CaptionData.java').write_text(caption_java)

# Import only the original drawable/color/font dependencies of these layouts.
for qualifier, nodes in selected.items():
    resources = ET.Element('resources')
    for node in nodes.values(): resources.append(node)
    path=DEST/qualifier/'donor.xml'; path.parent.mkdir(parents=True,exist_ok=True)
    ET.ElementTree(resources).write(path,encoding='utf-8',xml_declaration=True)

labels = ET.Element('resources')
ET.SubElement(labels,'string',name='app_name').text='NothingWidget'
ET.SubElement(labels,'style',name='AppTheme',parent='android:style/Theme.DeviceDefault.DayNight').extend([
    ET.fromstring('<item name="android:fontFamily">sans</item>'),
    ET.fromstring('<item name="android:colorAccent">#b9443b</item>'),
    ET.fromstring('<item name="android:windowActionModeOverlay">true</item>')])
for name,label,group,w,h,layout in variants:
    ET.SubElement(labels,'string',name='label_'+name.lower()).text=label
    period = 0 if group in ['Clocks','Date','Photos','Breathing','Compass'] else 1800000
    write('xml','widget_'+name.lower(),f'<appwidget-provider xmlns:android="{ANDROID}" android:minWidth="{max(40,w*70-30)}dp" android:minHeight="{max(40,h*70-30)}dp" android:minResizeWidth="40dp" android:minResizeHeight="40dp" android:targetCellWidth="{w}" android:targetCellHeight="{h}" android:initialLayout="@layout/{layout}" android:previewLayout="@layout/{layout}" android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen" android:updatePeriodMillis="{period}" android:configure="dev.dipa.widgets.ConfigureActivity" android:widgetFeatures="reconfigurable|configuration_optional"/>')
(DEST/'values').mkdir(exist_ok=True)
ET.ElementTree(labels).write(DEST/'values/app.xml',encoding='utf-8',xml_declaration=True)

manifest = ET.Element('manifest', {'package':'dev.dipa.widgets',f'{{{ANDROID}}}versionCode':'7',f'{{{ANDROID}}}versionName':'0.3.2'})
def element(parent,tag,**attrs): return ET.SubElement(parent,tag,{f'{{{ANDROID}}}{k}':str(v) for k,v in attrs.items()})
element(manifest,'uses-sdk',minSdkVersion=31,targetSdkVersion=36)
for permission in ['INTERNET','ACCESS_NETWORK_STATE','READ_CALENDAR','ACTIVITY_RECOGNITION','PACKAGE_USAGE_STATS','FOREGROUND_SERVICE','FOREGROUND_SERVICE_HEALTH','FOREGROUND_SERVICE_SPECIAL_USE','POST_NOTIFICATIONS','RECEIVE_BOOT_COMPLETED','BLUETOOTH_CONNECT']:
    element(manifest,'uses-permission',name='android.permission.'+permission)
element(manifest,'uses-feature',name='android.hardware.sensor.stepcounter',required='false')
element(manifest,'uses-feature',name='android.hardware.sensor.compass',required='false')
app=element(manifest,'application',label='@string/app_name',icon='@drawable/app_icon',theme='@style/AppTheme',allowBackup='false',supportsRtl='true',usesCleartextTraffic='false')
main=element(app,'activity',name='.MainActivity',exported='true')
flt=ET.SubElement(main,'intent-filter'); element(flt,'action',name='android.intent.action.MAIN'); element(flt,'category',name='android.intent.category.LAUNCHER')
cfg=element(app,'activity',name='.ConfigureActivity',exported='true')
flt=ET.SubElement(cfg,'intent-filter'); element(flt,'action',name='android.appwidget.action.APPWIDGET_CONFIGURE')
element(app,'activity',name='.SessionActivity',exported='false')
for name,label,group,w,h,layout in variants:
    receiver=element(app,'receiver',name='.Widgets$'+name,exported='false',label='@string/label_'+name.lower())
    flt=ET.SubElement(receiver,'intent-filter'); element(flt,'action',name='android.appwidget.action.APPWIDGET_UPDATE')
    element(receiver,'meta-data',name='android.appwidget.provider',resource='@xml/widget_'+name.lower())
element(app,'service',name='.RefreshJob',permission='android.permission.BIND_JOB_SERVICE',exported='false')
element(app,'service',name='.StepService',foregroundServiceType='health',exported='false')
compass=element(app,'service',name='.CompassService',foregroundServiceType='specialUse',exported='false')
element(compass,'property',name='android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE',value='User-enabled magnetic compass for placed home-screen widgets; sensors pause while locked or screen-off and artwork updates at most once per second.')
bluetooth_receiver=element(app,'receiver',name='.BluetoothBatteries',exported='true',permission='android.permission.BLUETOOTH_CONNECT')
flt=ET.SubElement(bluetooth_receiver,'intent-filter')
element(flt,'action',name='android.bluetooth.device.action.BATTERY_LEVEL_CHANGED')
element(flt,'action',name='android.bluetooth.device.action.ACL_DISCONNECTED')
for action in ['android.bluetooth.device.action.ACL_CONNECTED','android.bluetooth.adapter.action.STATE_CHANGED','android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED','android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED']:
    element(flt,'action',name=action)
service=element(app,'service',name='.MediaService',permission='android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',exported='true',label='NothingWidget media controls')
flt=ET.SubElement(service,'intent-filter'); element(flt,'action',name='android.service.notification.NotificationListenerService')
receiver=element(app,'receiver',name='.Events',exported='false')
flt=ET.SubElement(receiver,'intent-filter')
for action in ['BOOT_COMPLETED','MY_PACKAGE_REPLACED','DATE_CHANGED','TIME_SET','TIMEZONE_CHANGED','LOCALE_CHANGED','ACTION_POWER_CONNECTED','ACTION_POWER_DISCONNECTED','BATTERY_LOW','BATTERY_OKAY']:
    element(flt,'action',name='android.intent.action.'+action)
ET.ElementTree(manifest).write(ROOT/'app/AndroidManifest.xml',encoding='utf-8',xml_declaration=True)

registry='package dev.dipa.widgets;\npublic final class Registry {\n public static final String[][] ITEMS = {\n'
registry+='\n'.join('  {'+','.join(json.dumps(str(x),ensure_ascii=False) for x in row)+'},' for row in variants)
registry+='\n };\n public static String[] find(String name) { for(String[] row:ITEMS) if(row[0].equals(name)) return row; return ITEMS[0]; }\n}\n'
(ROOT/'app/src/dev/dipa/widgets/Registry.java').write_text(registry)
(ROOT/'source/imported-resources.json').write_text(json.dumps(sorted(set(dependencies)),indent=2)+'\n')
print(f'{len(variants)} native widget variants; {len(dependencies)} original resource files imported')
