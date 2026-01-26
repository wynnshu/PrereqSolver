'use client'

import { useState, KeyboardEvent } from 'react'
import { X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

interface TagInputProps {
  tags: string[]
  onTagsChange: (tags: string[]) => void
  placeholder?: string
  className?: string
}

export function TagInput({ tags, onTagsChange, placeholder, className }: TagInputProps) {
  const [inputValue, setInputValue] = useState('')

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && inputValue.trim()) {
      e.preventDefault()
      if (!tags.includes(inputValue.trim())) {
        onTagsChange([...tags, inputValue.trim()])
      }
      setInputValue('')
    } else if (e.key === 'Backspace' && !inputValue && tags.length > 0) {
      onTagsChange(tags.slice(0, -1))
    }
  }

  const removeTag = (tagToRemove: string) => {
    onTagsChange(tags.filter(tag => tag !== tagToRemove))
  }

  return (
    <div className={cn('relative', className)}>
      <div className="flex flex-wrap gap-2 p-3 rounded-lg border bg-background min-h-[56px]">
        {tags.map((tag) => (
          <Badge
            key={tag}
            variant="secondary"
            className="gap-1 pl-3 pr-2 py-1.5 bg-primary/10 text-primary hover:bg-primary/20 border-primary/20"
          >
            <span className="text-sm">{tag}</span>
            <button
              type="button"
              onClick={() => removeTag(tag)}
              className="rounded-full hover:bg-primary/20 p-0.5 transition-colors"
            >
              <X className="h-3 w-3" />
              <span className="sr-only">Remove {tag}</span>
            </button>
          </Badge>
        ))}
        <Input
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={tags.length === 0 ? placeholder : ''}
          className="flex-1 min-w-[120px] border-0 focus-visible:ring-0 focus-visible:ring-offset-0 px-0 py-0 h-auto bg-transparent"
        />
      </div>
    </div>
  )
}
