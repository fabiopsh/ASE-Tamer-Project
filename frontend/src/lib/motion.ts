/** Read the same CSS timing tokens used by stylesheets, without introducing React state. */
export function readMotionTokens() {
  const style = getComputedStyle(document.documentElement);
  const milliseconds = (name: string) => {
    const value = style.getPropertyValue(name).trim();
    return Number.parseFloat(value) * (value.endsWith('ms') ? 1 : 1000);
  };
  return {
    fast: milliseconds('--dur-fast'),
    base: milliseconds('--dur-base'),
    slow: milliseconds('--dur-slow'),
    easing: style.getPropertyValue('--ease-out').trim(),
    frame: style.getPropertyValue('--ease-frame').trim(),
  };
}
