"use client";

import { motion, useReducedMotion } from "motion/react";
import type { PropsWithChildren } from "react";
import { cn } from "../lib/cn";

export function Reveal({ children, className }: PropsWithChildren<{ className?: string }>) {
  const reduceMotion = useReducedMotion();

  return (
    <motion.div
      className={cn(className)}
      initial={reduceMotion ? false : { opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: reduceMotion ? 0 : 0.32, ease: "easeOut" }}
    >
      {children}
    </motion.div>
  );
}
