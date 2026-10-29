# AC1 · AC1 · Host long-presses a member's message

- Frame: `AC1 · Host long-presses a member's message`
- Node id: `12782:226`
- Size: 412x915
- Screenshot: `AC1.png`
- Caption: Silence and Remove always go in the selection bar's overflow. A 411dp bar holds four slots, so the ⋮ takes one and Report moves into the dropdown with them.

Coordinates are `@x,y WxH` in frame pixels (points on iOS, dp on Android) relative to the frame's top-left. `AL=V|H` is auto-layout direction; `pad` is top/right/bottom/left. `{name}` is a bound Figma variable (see tokens.md). Text rows list style name, or weight+size when no style is bound. The base screen is an instance of a shared base component and is not described beyond its name.

## Layers
```
Base: [Base / Android · Group transcript] @0,0 412x915
Selected row tint @0,657 412x128 fill=#ffffff@0.08
Selection top bar [Android / Selection top bar] @0,40 412x56
  5 round buttons 40x40 r=20 fill={backgroundRow}, y=48: arrow_back @15, content_copy @208, reply @257, forward @307, more_vert @357 (24x24 icon inset 8)
  Title "Yesterday, 4:13 PM" App/TextMedium fill={textMain} @67,58 136x19
DropdownMenu (opened from more_vert) [12801:1092] @140,96 260x161 fill=#303031 r=20 AL=V pad=8/0/8/0 fx=DROP_SHADOW
  Dropdown row [Android / Dropdown row] 260x48 AL=H gap=12 pad=0/16/0/16; icon 24x24 @x+16; label @x+52 App/TextMedium
  Report               @140,104  icon md.feedback            fill={Red}
  Divider              @140,152 260x1 fill=#ffffff@0.10
  Silence Grampa… (NEW) @140,153 icon speaker.slash          fill={textMain}
  Remove Grampa… (NEW)  @140,201 icon person.badge.minus    fill={Red}
```
