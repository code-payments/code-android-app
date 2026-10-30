# AC8a · AC8a · Silenced list

- Frame: `AC8a · Silenced list`
- Node id: `12782:242`
- Size: 412x915
- Screenshot: `AC8a.png`
- Caption: New ChatStep. Title bar and empty state follow BlocklistScreenContent; rows follow JoinRequests with a trailing Unsilence.

Coordinates are `@x,y WxH` in frame pixels (points on iOS, dp on Android) relative to the frame's top-left. `AL=V|H` is auto-layout direction; `pad` is top/right/bottom/left. `{name}` is a bound Figma variable (see tokens.md). Text rows list style name, or weight+size when no style is bound. The base screen is an instance of a shared base component and is not described beyond its name.

## Layers
```
Screen bg {backgroundMain}; no base instance (full screen)
Android status bar @0,0 412x24 ("9:41", Chat/Sender 12); Gesture bar @152,903 108x4 r=2 fill={textMain}
Nav: back Ellipse @16,54 40x40 fill=#ffffff@0.12 + chevron.left 20x20 @25,64; title "Silenced" Semi Bold 16 (no style) fill={textMain} @173,64 67x19 (centered)
Restricted member row 380x60 AL=H gap=12 pad=10/16/10/16
  Row 1 [Kind=Silenced] @16,118: "Grampa" / "Silenced · until 6:02 PM"
  Row 2 [Kind=Silenced forever] @16,182: "Web3theo" / "Silenced"
  Anatomy: Alert/Avatar 40x40 r=20 fill=#e8996b @x+16; text column AL=V gap=2 @x+68 (name App/TextMedium fill={textMain}; status App/Default13Medium fill={textSecondary}); trailing pill 95x33 fill=#ffffff@0.12 r=16 pad=8/14/8/14 "Unsilence" App/TextSmall fill={textMain}, right edge x+364
```
