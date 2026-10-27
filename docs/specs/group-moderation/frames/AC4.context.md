# AC4 · AC4 · Remove confirm

- Frame: `AC4 · Remove confirm`
- Node id: `12782:232`
- Size: 412x915
- Screenshot: `AC4.png`
- Caption: BottomBarManager.showAlert(showCancel = true), the same call ChatProfileViewModel makes for Block.

Coordinates are `@x,y WxH` in frame pixels (points on iOS, dp on Android) relative to the frame's top-left. `AL=V|H` is auto-layout direction; `pad` is top/right/bottom/left. `{name}` is a bound Figma variable (see tokens.md). Text rows list style name, or weight+size when no style is bound. The base screen is an instance of a shared base component and is not described beyond its name.

## Layers
```
Base: [Base / Android · Member profile (Grampa)] @0,0 412x915
Scrim @0,0 412x915 fill=#000000@0.45
BottomBar alert · Remove [12801:1280] @0,641 412x274 fill=#bc3434 AL=V gap=20 pad=30/16/16/16 (full-width, anchored to the bottom)
  Text group @16,687 380x64 AL=V gap=6
    "Remove Grampa?" App/TextLarge fill=#ffffff
    "They'll leave Jeffy | All Holders and can't rejoin until you unban them. Their messages stay in the chat" App/TextSmall fill=#ffffff
  Buttons @16,771 380x128 AL=V gap=8
    Primary @16,771 380x60 fill=#ffffff r=6, "Remove" App/TextMedium fill=#bc3434
    Secondary @16,839 380x60, "Cancel" App/TextMedium fill=#ffffff@0.50
```
