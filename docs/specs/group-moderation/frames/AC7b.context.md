# AC7b · AC7b · Moderation submenu open

- Frame: `AC7b · Moderation submenu open`
- Node id: `12782:240`
- Size: 412x915
- Screenshot: `AC7b.png`
- Caption: Material 3 has no submenu. The DropdownMenu swaps its items in place: a back row, then Silenced and Removed. The back row or system back returns.

Coordinates are `@x,y WxH` in frame pixels (points on iOS, dp on Android) relative to the frame's top-left. `AL=V|H` is auto-layout direction; `pad` is top/right/bottom/left. `{name}` is a bound Figma variable (see tokens.md). Text rows list style name, or weight+size when no style is bound. The base screen is an instance of a shared base component and is not described beyond its name.

## Layers
```
Base: [Base / Android · Group profile (Jeffy)] @0,0 412x915
DropdownMenu [12801:1552] @160,88 240x161 fill=#303031 r=20 AL=V pad=8/0/8/0 fx=DROP_SHADOW (same anchor and width as AC7a; height changes)
  Back row @160,96  240x48 icon md.arrow_back "Moderation" fill={textMain}
  Divider  @160,144 240x1 fill=#ffffff@0.10
  Silenced @160,145 240x48 icon speaker.slash                   "Silenced" fill={textMain}
  Removed  @160,193 240x48 icon person.crop.circle.badge.xmark  "Removed"  fill={textMain}
Note the Removed icon differs from iOS (person.badge.minus on iOS C7b).
```
