# AC7a · AC7a · Group overflow (host)

- Frame: `AC7a · Group overflow (host)`
- Node id: `12782:238`
- Size: 412x915
- Screenshot: `AC7a.png`
- Caption: Moderation › in the group DropdownMenu, host only, after Mute Notifications and before the Report divider.

Coordinates are `@x,y WxH` in frame pixels (points on iOS, dp on Android) relative to the frame's top-left. `AL=V|H` is auto-layout direction; `pad` is top/right/bottom/left. `{name}` is a bound Figma variable (see tokens.md). Text rows list style name, or weight+size when no style is bound. The base screen is an instance of a shared base component and is not described beyond its name.

## Layers
```
Base: [Base / Android · Group profile (Jeffy)] @0,0 412x915
DropdownMenu [12801:1458] @160,88 240x209 fill=#303031 r=20 AL=V pad=8/0/8/0 fx=DROP_SHADOW
  Encryption         @160,96  240x48 icon lock.open  "Encryption" fill={textMain}
  Mute Notifications @160,144 240x48 icon bell.slash "Mute Notifications" fill={textMain}
  Moderation (NEW) [12801:1481] @160,192 240x48 AL=H gap=12 pad=0/16/0/16
    icon shield @176,204; "Moderation" App/TextMedium fill={textMain} @212,207; trailing icon md.chevron_right 24x24 @360,204 (a hidden duplicate "Trailing" instance also exists, ignore)
  Divider            @160,240 240x1 fill=#ffffff@0.10
  Report             @160,241 240x48 icon md.feedback "Report" fill={Red}
Rows: App/TextMedium label, icon 24x24.
```
