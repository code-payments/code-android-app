# AC2 · AC2 · Member profile menu (host)

- Frame: `AC2 · Member profile menu (host)`
- Node id: `12782:228`
- Size: 412x915
- Screenshot: `AC2.png`
- Caption: Profile inside a group's chat flow. Gate on the flow's chat being a group, not ProfileOrigin: an avatar tap in the transcript arrives as Chat.

Coordinates are `@x,y WxH` in frame pixels (points on iOS, dp on Android) relative to the frame's top-left. `AL=V|H` is auto-layout direction; `pad` is top/right/bottom/left. `{name}` is a bound Figma variable (see tokens.md). Text rows list style name, or weight+size when no style is bound. The base screen is an instance of a shared base component and is not described beyond its name.

## Layers
```
Base: [Base / Android · Member profile (Grampa)] @0,0 412x915
DropdownMenu [12801:1152] @164,88 236x238 fill=#303031 r=20 AL=V pad=8/0/8/0 fx=DROP_SHADOW
  Report @164,96  236x48 icon md.feedback "Report" fill={Red}
  Block  @164,144 236x48 icon nosign       "Block"  fill={Red}
  Divider @164,192 236x1 fill=#ffffff@0.10
  Section title (NEW) [12801:1169] @164,193 236x29 pad=10/0/4/16: "In Jeffy | All Holders" App/TextCaption fill={textSecondary} @180,203
  Silence… (NEW)           @164,222 236x48 icon speaker.slash       "Silence…" fill={textMain}
  Remove from Group (NEW)  @164,270 236x48 icon person.badge.minus  "Remove from Group" fill={Red}
Rows: AL=H gap=12 pad=0/16/0/16, icon 24x24, label App/TextMedium. Section title text is the group name. Note: no Mute Notifications row in this menu.
```
