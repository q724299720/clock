---
name: Luminous Utility
colors:
  surface: '#faf8ff'
  surface-dim: '#d9d9e5'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3fe'
  surface-container: '#ededf9'
  surface-container-high: '#e7e7f3'
  surface-container-highest: '#e1e2ed'
  on-surface: '#191b23'
  on-surface-variant: '#434655'
  inverse-surface: '#2e3039'
  inverse-on-surface: '#f0f0fb'
  outline: '#737686'
  outline-variant: '#c3c6d7'
  surface-tint: '#0053db'
  primary: '#004ac6'
  on-primary: '#ffffff'
  primary-container: '#2563eb'
  on-primary-container: '#eeefff'
  inverse-primary: '#b4c5ff'
  secondary: '#545f73'
  on-secondary: '#ffffff'
  secondary-container: '#d5e0f8'
  on-secondary-container: '#586377'
  tertiary: '#46566c'
  on-tertiary: '#ffffff'
  tertiary-container: '#5e6e85'
  on-tertiary-container: '#e9f0ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b4c5ff'
  on-primary-fixed: '#00174b'
  on-primary-fixed-variant: '#003ea8'
  secondary-fixed: '#d8e3fb'
  secondary-fixed-dim: '#bcc7de'
  on-secondary-fixed: '#111c2d'
  on-secondary-fixed-variant: '#3c475a'
  tertiary-fixed: '#d3e4fe'
  tertiary-fixed-dim: '#b7c8e1'
  on-tertiary-fixed: '#0b1c30'
  on-tertiary-fixed-variant: '#38485d'
  background: '#faf8ff'
  on-background: '#191b23'
  surface-variant: '#e1e2ed'
typography:
  display-time:
    fontFamily: Hanken Grotesk
    fontSize: 64px
    fontWeight: '700'
    lineHeight: 72px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-caps:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  container-padding: 1.25rem
  stack-gap: 1rem
  grid-gutter: 1rem
  card-padding: 1.5rem
---

## Brand & Style
The design system focuses on cognitive clarity and immediate legibility, essential for an application used during early mornings or late nights. The brand personality is dependable, precise, and unobtrusive. 

The aesthetic leverages **Minimalism** with a focus on high-contrast functional elements. By utilizing a "White-on-Tint" approach, the UI establishes a clear workspace hierarchy without relying on heavy shadows or complex textures. The emotional response should be one of calm organization—reducing the friction of scheduling and task management through a spacious, airy interface that feels light yet structurally sound.

## Colors
The palette is rooted in a cool, functional spectrum. 
- **Background**: A clean grey-blue (#f4f7fb) acts as a canvas to make pure white surfaces "pop."
- **Surface**: Workspace elements and cards utilize pure white (#ffffff) to maximize contrast with text.
- **Primary**: A vibrant blue-to-deep-blue gradient is reserved for high-priority interactive elements like primary buttons and active states.
- **Text**: Main information uses a deep Slate (#1e293b) for AAA-rated accessibility. Secondary metadata uses a mid-tone Slate (#64748b) to maintain hierarchy without sacrificing readability.

## Typography
The system uses **Hanken Grotesk** for its contemporary, sharp geometric qualities that ensure legibility at any size. Large "Display Time" styles allow for instant recognition of alarm settings. 

**JetBrains Mono** is introduced sparingly for labels and technical data (like dates or duration countdowns) to provide a "tool-like" precision. 
- Use **Bold/700** for primary headlines to anchor the page.
- Use **Regular/400** for body text to maintain an airy feel.
- Ensure all time-based displays use tabular figures to prevent horizontal jumping during countdowns.

## Layout & Spacing
This design system employs a fluid-width model tailored for mobile constraints. It uses an **8px base grid** for all spacing increments.
- **Margins**: A 20px (1.25rem) safety margin is applied to the left and right of the screen.
- **Card Spacing**: Vertical stacks of alarm cards use a 16px (1rem) gap to maintain distinct tap targets.
- **Touch Targets**: All interactive elements (toggles, list items) must maintain a minimum height of 48px to ensure ease of use during groggy morning interactions.

## Elevation & Depth
Depth is created through **Low-contrast outlines** rather than shadows. 
- **Level 0 (Background)**: #f4f7fb.
- **Level 1 (Cards/Surface)**: White background with a 1px solid #e2e8f0 border.
- **Halo Effect**: On active states or focused inputs, a subtle 4px blur "halo" using the primary blue at 10% opacity may be used to indicate focus without adding visual weight.
- No heavy drop shadows are permitted. Surfaces should feel like they are resting flat on the tinted background.

## Shapes
A **Rounded** (Level 2) shape language is applied to balance the "clinical" feel of the high-contrast palette.
- **Standard Cards**: 1rem (16px) corner radius.
- **Buttons**: 0.75rem (12px) for a modern, friendly touch.
- **Small Elements (Chips/Toggles)**: 0.5rem (8px) or fully pill-shaped for toggles.
The consistency in roundedness ensures that even with high-contrast borders, the UI feels approachable.

## Components
- **Alarm Cards**: Large, white containers with a 1px #e2e8f0 border. The time should be the hero element in `headline-lg`. A prominent toggle switch sits on the right.
- **Toggle Switches**: High-contrast design. Off state is a neutral grey; On state uses the primary blue-to-deep-blue gradient.
- **Primary Buttons**: Fixed-height (56px) with the primary gradient and white text. These should span the width of the container minus margins.
- **Calendar Grid**: A clean, borderless grid of numbers using `body-md`. Selected dates are highlighted with a solid blue circle and white text.
- **Bottom Navigation**: A pure white bar with a subtle top border (#e2e8f0). Icons use #64748b when inactive and the primary blue gradient when active.
- **Input Fields**: Ghost-style inputs with #f4f7fb backgrounds and thin #e2e8f0 borders, transitioning to a blue border on focus.