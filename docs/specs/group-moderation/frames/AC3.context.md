# AC3 · AC3 · Silence duration sheet

- Frame: `AC3 · Silence duration sheet`
- Node id: `12782:230`
- Size: 412x915
- Screenshot: `AC3.png`
- Caption: MuteChatSheet with its options passed in. SilenceOption has the same durations; the last row says Forever.

Coordinates are `@x,y WxH` in frame pixels (points on iOS, dp on Android) relative to the frame's top-left. `AL=V|H` is auto-layout direction; `pad` is top/right/bottom/left. `{name}` is a bound Figma variable (see tokens.md). Text rows list style name, or weight+size when no style is bound. The base screen is an instance of a shared base component and is not described beyond its name.

## Layers
```
Base: [Base / Android · Group transcript] @0,0 412x915
Scrim @0,0 412x915 fill=#000000@0.50
Silence sheet [12801:1233] @13,473 386x426 fill={backgroundMain} r=36 AL=V gap=12 pad=56/16/24/16
  Header @29,529 354x44: "Silence Grampa" Semi Bold 16 (no style) centered @145,541; close Ellipse @347,533 36x36 fill=#ffffff@0.15 + icon xmark 16x16
  Body "They can still read and react in Jeffy | All Holders, but can't send messages. Only they're told" App/TextSmall fill={textSecondary} @29,585 354x34
  Duration rows ×4 (354x52 fill={backgroundRow} r=12 AL=H gap=12 pad=0/16/0/16; label App/TextMedium fill={textMain}); gap 12
    @29,631 icon clock "1 Hour" | @29,695 icon clock "8 Hours" | @29,759 icon clock "1 Week" | @29,823 icon nosign "Forever"
```
