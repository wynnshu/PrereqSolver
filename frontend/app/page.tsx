'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { TagInput } from '@/components/tag-input'
import { ArrowRight, GraduationCap, Loader2, BookOpen, TrendingUp } from 'lucide-react'
import { Badge } from '@/components/ui/badge'

const API_URL = 'https://prereqsolver-670505977045.us-west1.run.app'
type CoursePath = string[]

export default function CoursePlanner() {
  const [showPlanner, setShowPlanner] = useState(false)
  const [desiredCourses, setDesiredCourses] = useState<string[]>([])
  const [completedCourses, setCompletedCourses] = useState<string[]>([])
  const [coursePaths, setCoursePaths] = useState<CoursePath[]>([])
  const [isLoading, setIsLoading] = useState(false)

  const handlePlanPath = async () => {
    setIsLoading(true)
    setCoursePaths([])
    if (desiredCourses.length == 0) {
      alert('Enter at least one desired course.')
      return
    }
    try {
      console.log('[v0] Sending request with:', {
        targetCourse: desiredCourses,
        takenCourses: completedCourses,
      })
      
      const firstDesiredCourse = desiredCourses.at(0);
      if (desiredCourses.length > 1) {
        console.log("Note: multiple desired courses currently unsupported, fetching paths for first course entered.")
      }
      
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          targetCourse: firstDesiredCourse,
          takenCourses: completedCourses,
        }),
      })

      console.log('[v0] Response status:', response.status)
      console.log('[v0] Response headers:', response.headers.get('content-type'))

      if (!response.ok) {
        const text = await response.text()
        console.error('[v0] Error response:', text)
        throw new Error(`Server returned ${response.status}: ${response.statusText}`)
      }

      const contentType = response.headers.get('content-type')
      if (!contentType || !contentType.includes('application/json')) {
        const text = await response.text()
        console.error('[v0] Non-JSON response:', text.substring(0, 200))
        throw new Error('Server did not return JSON. Please check your API endpoint.')
      }

      const data = await response.json()
      console.log('[v0] Received data:', data)

      // Sort paths by number of courses (shortest first)
      const sortedPaths = (data.paths || data || []).sort(
        (a: CoursePath, b: CoursePath) => a.length - b.length
      )
      setCoursePaths(sortedPaths)
    } catch (error) {
      console.error('[v0] Error fetching course paths:', error)
      alert(
        `Failed to fetch course paths: ${error instanceof Error ? error.message : 'Unknown error'}. Please ensure your server is running and the API endpoint is correct.`
      )
    } finally {
      setIsLoading(false)
    }
  }

  if (!showPlanner) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="max-w-4xl w-full">
          <div className="text-center space-y-8">
            <div className="space-y-4">
              <div className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-primary/10 mb-4">
                <GraduationCap className="w-10 h-10 text-primary" />
              </div>
              <h1 className="text-5xl md:text-6xl font-bold tracking-tight text-balance">
                Plan Your <span className="text-primary">Academic Path</span>
              </h1>
              <p className="text-xl text-muted-foreground max-w-2xl mx-auto text-balance leading-relaxed">
                {'Discover the optimal course sequences to reach your academic goals. Our intelligent planner analyzes prerequisites and your progress to map out clear paths forward.'}
              </p>
            </div>

            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Button
                size="lg"
                onClick={() => setShowPlanner(true)}
                className="text-base px-8 h-12"
              >
                Get Started
                <ArrowRight className="ml-2 h-5 w-5" />
              </Button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-16 text-left">
              <Card className="border-primary/20">
                <CardHeader>
                  <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center mb-2">
                    <BookOpen className="w-6 h-6 text-primary" />
                  </div>
                  <CardTitle className="text-lg">Smart Prerequisites</CardTitle>
                </CardHeader>
                <CardContent>
                  <CardDescription className="leading-relaxed">
                    Automatically considers course prerequisites and dependencies to ensure valid paths
                  </CardDescription>
                </CardContent>
              </Card>

              <Card className="border-accent/20">
                <CardHeader>
                  <div className="w-12 h-12 rounded-lg bg-accent/10 flex items-center justify-center mb-2">
                    <TrendingUp className="w-6 h-6 text-accent" />
                  </div>
                  <CardTitle className="text-lg">Optimized Paths</CardTitle>
                </CardHeader>
                <CardContent>
                  <CardDescription className="leading-relaxed">
                    Find the shortest route to your goals, sorted by total course count
                  </CardDescription>
                </CardContent>
              </Card>

              <Card className="border-primary/20">
                <CardHeader>
                  <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center mb-2">
                    <GraduationCap className="w-6 h-6 text-primary" />
                  </div>
                  <CardTitle className="text-lg">Track Progress</CardTitle>
                </CardHeader>
                <CardContent>
                  <CardDescription className="leading-relaxed">
                    Input completed courses to get personalized recommendations
                  </CardDescription>
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background">
      <div className="max-w-6xl mx-auto p-4 md:p-8 space-y-8">
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowPlanner(false)}
              className="text-muted-foreground hover:text-foreground"
            >
              ← Back
            </Button>
          </div>
          <h1 className="text-4xl font-bold tracking-tight">Course Path Planner</h1>
          <p className="text-muted-foreground text-balance leading-relaxed">
            {'Enter your desired courses and completed coursework to discover the optimal paths to achieve your academic goals.'}
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card>
            <CardHeader>
              <CardTitle>Desired Courses</CardTitle>
              <CardDescription>
                Enter the courses you want to take (press Enter to add)
              </CardDescription>
            </CardHeader>
            <CardContent>
              <TagInput
                tags={desiredCourses}
                onTagsChange={setDesiredCourses}
                placeholder="e.g., CS 101, MATH 201..."
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Completed Courses</CardTitle>
              <CardDescription>
                Enter the courses you have already completed (press Enter to add)
              </CardDescription>
            </CardHeader>
            <CardContent>
              <TagInput
                tags={completedCourses}
                onTagsChange={setCompletedCourses}
                placeholder="e.g., CS 100, MATH 101..."
              />
            </CardContent>
          </Card>
        </div>

        <div className="flex justify-center">
          <Button
            size="lg"
            onClick={handlePlanPath}
            disabled={desiredCourses.length === 0 || isLoading}
            className="px-8 h-12"
          >
            {isLoading ? (
              <>
                <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                Calculating Paths...
              </>
            ) : (
              <>
                Generate Course Paths
                <ArrowRight className="ml-2 h-5 w-5" />
              </>
            )}
          </Button>
        </div>

        {coursePaths.length > 0 && (
          <div className="space-y-4">
            <div className="flex items-baseline gap-3">
              <h2 className="text-2xl font-bold">Available Paths</h2>
              <span className="text-sm text-muted-foreground">
                {coursePaths.length} {coursePaths.length === 1 ? 'path' : 'paths'} found, sorted by length
              </span>
            </div>

            <div className="grid gap-4">
              {coursePaths.map((path, index) => (
                <Card key={index} className="border-primary/20 hover:border-primary/40 transition-colors">
                  <CardHeader>
                    <div className="flex items-start justify-between gap-4">
                      <div className="space-y-1">
                        <CardTitle className="text-lg">Path {index + 1}</CardTitle>
                        <CardDescription>
                          {path.length} {path.length === 1 ? 'course' : 'courses'} required
                        </CardDescription>
                      </div>
                      <Badge variant="secondary" className="shrink-0 bg-primary/10 text-primary border-primary/20">
                        {path.length} {path.length === 1 ? 'Course' : 'Courses'}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="flex flex-wrap gap-2">
                      {path.map((course, courseIndex) => (
                        <div key={courseIndex} className="flex items-center gap-2">
                          <Badge variant="outline" className="px-3 py-1.5 text-sm">
                            {course}
                          </Badge>
                          {courseIndex < path.length - 1 && (
                            <ArrowRight className="h-4 w-4 text-muted-foreground" />
                          )}
                        </div>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>
        )}

        {!isLoading && coursePaths.length === 0 && desiredCourses.length > 0 && (
          <Card className="border-muted">
            <CardContent className="pt-6">
              <p className="text-center text-muted-foreground">
                Click "Generate Course Paths" to discover possible routes to your desired courses
              </p>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
