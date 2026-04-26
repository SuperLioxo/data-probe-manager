# Design System - Data Probe Manager

## Overview
This document defines the unified design system for the Data Probe Manager application. All components should follow these guidelines to ensure visual consistency across the entire application.

## Color Palette

### Primary Colors
- **Primary 50**: #EFF6FF - Lightest blue background
- **Primary 100**: #DBEAFE - Very light blue
- **Primary 200**: #BFDBFE - Light blue
- **Primary 300**: #93C5FD - Medium-light blue
- **Primary 400**: #60A5FA - Medium blue
- **Primary 500**: #3B82F6 - **Main primary color**
- **Primary 600**: #2563EB - Dark blue (hover state)
- **Primary 700**: #1D4ED8 - Darker blue
- **Primary 800**: #1E40AF - Very dark blue (sidebar)
- **Primary 900**: #1E3A8A - Darkest blue

### Semantic Colors

#### Success
- **Success 100**: #D1FAE5
- **Success 700**: #047857
- **Success**: #10B981

#### Warning
- **Warning 100**: #FEF3C7
- **Warning 700**: #B45309
- **Warning**: #F59E0B

#### Error
- **Error 100**: #FEE2E2
- **Error 700**: #B91C1C
- **Error**: #EF4444

#### Info
- **Info 100**: #DBEAFE
- **Info 700**: #1D4ED8
- **Info**: #3B82F6

### Background Colors
- **bg-primary**: #F8FAFC - Main page background
- **bg-secondary**: #F1F5F9 - Secondary background
- **bg-tertiary**: #E2E8F0 - Third level background
- **bg-card**: #ffffff - Card background
- **bg-hover**: #F1F5F9 - Hover state background
- **bg-active**: #E2E8F0 - Active state background

### Text Colors
- **text-primary**: #0F172A - Primary text (high contrast)
- **text-secondary**: #475569 - Secondary text
- **text-tertiary**: #94A3B8 - Tertiary text
- **text-disabled**: #CBD5E1 - Disabled text
- **text-inverse**: #ffffff - Inverse text

### Border Colors
- **border-color**: #e0e0e0 - Default border
- **border-color-light**: rgba(0, 0, 0, 0.06) - Light border
- **border-color-medium**: rgba(0, 0, 0, 0.1) - Medium border
- **border-color-dark**: rgba(0, 0, 0, 0.15) - Dark border
- **border-hover**: #d0d0d0 - Hover border

## Typography

### Font Families
- **Font Sans**: 'Fira Sans', 'Inter', sans-serif
- **Font Mono**: 'Fira Code', 'JetBrains Mono', monospace
- **Font Heading**: 'Fira Code', monospace
- **Font Chinese**: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei'

### Font Sizes
- **text-xs**: 12px
- **text-sm**: 14px
- **text-base**: 16px
- **text-lg**: 18px
- **text-xl**: 20px
- **text-2xl**: 24px
- **text-3xl**: 30px
- **text-4xl**: 36px

### Font Weights
- **font-weight-normal**: 400
- **font-weight-medium**: 500
- **font-weight-semibold**: 600
- **font-weight-bold**: 700

### Line Heights
- **leading-none**: 1
- **leading-tight**: 1.25
- **leading-normal**: 1.5
- **leading-relaxed**: 1.75

## Spacing Scale

Based on 4px units:
- **spacing-1**: 4px
- **spacing-2**: 8px
- **spacing-3**: 12px
- **spacing-4**: 16px
- **spacing-5**: 20px
- **spacing-6**: 24px
- **spacing-8**: 32px
- **spacing-10**: 40px
- **spacing-12**: 48px
- **spacing-16**: 64px

## Border Radius

- **border-radius-sm**: 6px
- **border-radius-md**: 8px
- **border-radius-lg**: 12px
- **border-radius-xl**: 16px
- **border-radius-full**: 9999px

## Shadows

- **shadow-xs**: 0 1px 2px 0 rgba(0, 0, 0, 0.03)
- **shadow-sm**: 0 1px 3px 0 rgba(0, 0, 0, 0.06), 0 1px 2px -1px rgba(0, 0, 0, 0.06)
- **shadow-md**: 0 4px 6px -1px rgba(0, 0, 0, 0.08), 0 2px 4px -2px rgba(0, 0, 0, 0.06)
- **shadow-lg**: 0 10px 15px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -4px rgba(0, 0, 0, 0.05)
- **shadow-xl**: 0 20px 25px -5px rgba(0, 0, 0, 0.08), 0 8px 10px -6px rgba(0, 0, 0, 0.04)
- **shadow-card**: 0 2px 12px rgba(0, 0, 0, 0.08)
- **shadow-card-hover**: 0 8px 24px rgba(0, 0, 0, 0.12)

## Transitions

### Duration
- **duration-instant**: 100ms
- **duration-fast**: 150ms
- **duration-normal**: 200ms
- **duration-slow**: 300ms
- **duration-slower**: 500ms

### Easing Functions
- **ease-out**: cubic-bezier(0.215, 0.61, 0.355, 1)
- **ease-in-out**: cubic-bezier(0.645, 0.045, 0.355, 1)
- **ease-smooth**: cubic-bezier(0.4, 0, 0.2, 1)

### Transition Presets
- **transition-base**: all 200ms cubic-bezier(0.4, 0, 0.2, 1)
- **transition-colors**: color 150ms cubic-bezier(0.4, 0, 0.2, 1), background-color 150ms cubic-bezier(0.4, 0, 0.2, 1)
- **transition-button**: all 150ms cubic-bezier(0.4, 0, 0.2, 1)
- **transition-input**: border-color 150ms cubic-bezier(0.4, 0, 0.2, 1), box-shadow 150ms cubic-bezier(0.4, 0, 0.2, 1)

## Gradients

- **gradient-primary**: linear-gradient(135deg, #3B82F6 0%, #1E40AF 100%)
- **gradient-primary-hover**: linear-gradient(135deg, #2563EB 0%, #1E3A8A 100%)
- **gradient-success**: linear-gradient(135deg, #10B981 0%, #059669 100%)
- **gradient-warning**: linear-gradient(135deg, #F59E0B 0%, #D97706 100%)
- **gradient-error**: linear-gradient(135deg, #EF4444 0%, #DC2626 100%)
- **gradient-card**: linear-gradient(135deg, #F1F5F9 0%, #ffffff 100%)
- **gradient-header**: linear-gradient(135deg, #ffffff 0%, #F8FAFC 100%)

## Component Guidelines

### Buttons
- Use gradient backgrounds for primary buttons
- Add subtle hover effects (translateY(-1px))
- Include shadow on hover
- Transition duration: 150ms

### Cards
- Background: white or gradient-card
- Border: 1px solid border-color-light
- Border radius: border-radius-lg
- Shadow: shadow-sm (shadow-md on hover)
- Padding: spacing-5 or spacing-6
- Transition: 200ms with subtle translateY(-2px) on hover

### Table Headers
- Background: bg-secondary
- Text color: text-secondary
- Font weight: font-weight-semibold
- Text transform: uppercase
- Font size: text-xs
- Letter spacing: 0.05em

### Form Elements
- Border radius: border-radius-md
- Min height: 44px (touch target)
- Focus state: 3px ring with primary color at 10% opacity
- Transition: 150ms

### Menu Items
- Margin: 0 6px
- Border radius: 6px
- Hover: rgba(255, 255, 255, 0.08) background + translateX(2px)
- Active: gradient from primary-600 to primary-700
- White left border indicator (3px width)

### Tags/Labels
- Border radius: border-radius-full
- Padding: 4px 12px
- Font weight: font-weight-medium
- No border
- Use semantic color backgrounds (100 shades)

## Accessibility

### Color Contrast
- Ensure minimum 4.5:1 contrast ratio for normal text
- Ensure minimum 3:1 contrast ratio for large text
- Use color-contrast tool to verify

### Touch Targets
- Minimum size: 44x44px
- Apply `.touch-target` class to interactive elements

### Focus States
- Visible outline: 2px solid primary color
- Outline offset: 2px
- Use `:focus-visible` to show only keyboard focus

### Screen Readers
- Use `.sr-only` class for screen reader-only content
- Provide aria-labels for icon-only buttons
- Use semantic HTML elements

## Responsive Breakpoints

- **Mobile**: < 576px
- **Tablet**: 576px - 768px
- **Desktop**: 768px - 1024px
- **Large Desktop**: > 1024px

## Performance

### Animations
- Respect `prefers-reduced-motion`
- Use transform/opacity for animations (not width/height)
- Keep animations under 300ms for micro-interactions

### Rendering
- Use `will-change` sparingly
- Use GPU acceleration for animations (`transform: translateZ(0)`)
- Optimize shadow rendering

## Best Practices

1. **Always use CSS variables** instead of hardcoded values
2. **Maintain consistent spacing** using the spacing scale
3. **Use semantic color names** (primary, success, warning, error)
4. **Apply transitions consistently** using transition presets
5. **Ensure proper contrast ratios** for accessibility
6. **Test in both light and dark modes** (even if only light is used)
7. **Use semantic HTML** elements
8. **Provide visual feedback** for all interactive elements
9. **Keep animations subtle** and purposeful
10. **Test on real devices** for touch interactions

## Migration Checklist

When updating components to match the design system:

- [ ] Replace all hardcoded colors with CSS variables
- [ ] Update font families to use design system fonts
- [ ] Apply consistent border radius values
- [ ] Use design system spacing scale
- [ ] Add proper transitions (150-200ms)
- [ ] Ensure hover states provide feedback
- [ ] Verify focus states are visible
- [ ] Check color contrast ratios
- [ ] Test responsive behavior
- [ ] Validate accessibility

## Resources

- Theme variables: `/src/assets/theme.css`
- Element Plus customization: `/src/assets/theme-element-plus.css`
- This document: `/src/assets/design-system.md`
